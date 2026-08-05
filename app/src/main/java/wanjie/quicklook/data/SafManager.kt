package wanjie.quicklook.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri

/**
 * SAF（Storage Access Framework）目录管理：持久化权限 + DocumentFile 列举。
 * 所有方法应在 IO 线程调用。
 */
class SafManager(private val context: Context) {

    /** 持久化取得 tree URI 的访问权限。在 Activity 回调中调用。 */
    fun takePermission(treeUri: Uri) {
        runCatching {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }.onFailure { Log.e(TAG, "takePermission failed", it) }
    }

    /** 释放权限并返回是否成功 */
    fun releasePermission(treeUri: Uri) {
        runCatching {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(treeUri, flags)
        }.onFailure { Log.e(TAG, "releasePermission failed", it) }
    }

    /** 权限是否仍有效 */
    fun isAuthorized(treeUri: Uri): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri }
    }.getOrDefault(false)

    /** 根据名称取 SAF 目录显示名（DocumentFile.name） */
    fun displayName(treeUri: Uri): String? = runCatching {
        DocumentFile.fromTreeUri(context, treeUri)?.name
    }.getOrNull()

    /**
     * 列举 SAF 目录（tree URI 或其后代 document URI）的直接子项。
     * @param uri tree URI 或子文档 URI 字符串
     */
    fun listDirectory(uri: String, config: SortConfig, showHidden: Boolean): List<FileItem> {
        val doc = resolve(uri) ?: return emptyList()
        if (!doc.isDirectory) return emptyList()
        val children = doc.listFiles()
        val items = children.mapNotNull { d ->
            if (!d.exists()) return@mapNotNull null
            val name = d.name ?: return@mapNotNull null
            if (!showHidden && name.startsWith(".")) return@mapNotNull null
            val isDir = d.isDirectory
            val mime = if (isDir) "inode/directory" else d.type ?: FileUtils.mimeTypeFor(name)
            FileItem(
                uri = d.uri,
                name = name,
                path = d.uri.toString(),   // SAF 模式下 path 即 document URI 字符串
                isDirectory = isDir,
                size = if (isDir) 0L else d.length(),
                lastModified = d.lastModified(),
                mimeType = mime,
                category = FileUtils.categoryFor(name, isDir, mime),
            )
        }
        return FileUtils.applySort(items, config)
    }

    /**
     * 构建 SAF 目录的面包屑：从根 tree 到当前 document 的层级。
     *
     * 不依赖 [DocumentFile.getParentFile]（该接口在很多 provider/版本上返回 null，
     * 会导致面包屑只显示当前目录）。改为解析 document URI 的 document id：
     * 对 externalstorage 这类 provider，document id 形如 `primary:Foo/bar/baz`，
     * 按 `/` 分割 root 之后的部分即可还原每级目录，再用
     * [DocumentsContract.buildDocumentUriUsingTree] 构造每级 URI。
     *
     * @param rootTreeUri 根 tree URI 字符串
     * @param currentUri 当前 document URI 字符串（可为根）
     * @param rootName 根节点显示名（通常为书签名），为空则尝试用 DocumentFile.name
     */
    fun breadcrumbs(rootTreeUri: String, currentUri: String, rootName: String? = null): List<BreadcrumbSegment> {
        val treeUri = rootTreeUri.toUri()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        // 当前 document id：若 currentUri 本身是 tree URI（根），用 treeDocId
        val curUri = currentUri.toUri()
        val curDocId = runCatching {
            if (DocumentsContract.isDocumentUri(context, curUri)) {
                DocumentsContract.getDocumentId(curUri)
            } else {
                DocumentsContract.getTreeDocumentId(curUri)
            }
        }.getOrNull() ?: rootDocId

        // 从根 docId 到当前 docId 的每一级
        val docIds = mutableListOf(rootDocId)
        if (curDocId.length > rootDocId.length && curDocId.startsWith(rootDocId)) {
            val rest = curDocId.substring(rootDocId.length).trimStart('/')
            if (rest.isNotEmpty()) {
                var acc = rootDocId
                rest.split('/').forEach { seg ->
                    if (seg.isNotEmpty()) {
                        acc = "$acc/$seg"
                        docIds.add(acc)
                    }
                }
            }
        }

        return docIds.mapIndexed { i, docId ->
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val name = when {
                i == 0 && !rootName.isNullOrBlank() -> rootName
                else -> {
                    // 取 document id 最后一段作为名称；根特殊处理用 DocumentFile.name
                    val seg = docId.substringAfterLast('/')
                    if (seg.isEmpty()) docId.substringAfter(':').ifBlank { docId }
                    else seg
                }
            }.ifBlank { rootName ?: "SAF" }
            BreadcrumbSegment(name = name, uri = uri)
        }
    }

    /** 判断 URI 是否为目录 */
    fun isDirectory(uri: String): Boolean = resolve(uri)?.isDirectory == true

    /** 解析提供该 SAF 目录的 provider 应用名（通过 authority 找到应用 label） */
    fun providerName(uri: String): String? = runCatching {
        val authority = Uri.parse(uri).authority ?: return null
        val pm = context.packageManager
        pm.resolveContentProvider(authority, 0)
            ?.applicationInfo
            ?.let { pm.getApplicationLabel(it).toString() }
    }.getOrNull()

    private fun resolve(uri: String): DocumentFile? {
        val u = uri.toUri()
        return runCatching {
            DocumentFile.fromTreeUri(context, u) ?: DocumentFile.fromSingleUri(context, u)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "SafManager"
    }
}
