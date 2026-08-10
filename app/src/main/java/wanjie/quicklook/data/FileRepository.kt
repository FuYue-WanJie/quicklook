package wanjie.quicklook.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository boundary between UI and file-system / MediaStore.
 * Uses java.io.File for the browser (works for app-private and shared external storage paths
 * that are reachable without SAF on API 26+).
 */
class FileRepository(private val context: Context) {

    private val externalRoot: File = android.os.Environment.getExternalStorageDirectory()
    private val downloadsRoot: File = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOWNLOADS
    )
    private val appRoot: File = context.getExternalFilesDir(null) ?: context.filesDir
    private val shizukuManager = ShizukuManager(context)

    /** 通过 Shizuku 可访问的 /sdcard/ 根目录 */
    val shizukuRoot: File get() = File("/sdcard/")

    /**  roots  shown  on  the  Storage  tab.  */
    fun roots(): List<StorageRoot> = buildList {
        add(StorageRoot("内部存储", externalRoot.toUri(), externalRoot))
        add(StorageRoot("下载", downloadsRoot.toUri(), downloadsRoot))
        add(StorageRoot("应用文件", appRoot.toUri(), appRoot))
        if (shizukuManager.isAvailable && shizukuManager.isGranted) {
            add(StorageRoot("Shizuku 存储", shizukuRoot.toUri(), shizukuRoot, isShizuku = true))
        }
    }

    suspend fun listDirectory(dir: File, config: SortConfig, showHidden: Boolean, isShizuku: Boolean = false): List<FileItem> =
        withContext(Dispatchers.IO) {
            if (isShizuku && shizukuManager.canAccess(dir.absolutePath)) {
                shizukuManager.listDirectoryViaShizuku(dir.absolutePath, config, showHidden)
                    ?: emptyList()
            } else {
                FileUtils.listFiles(dir)?.let { FileUtils.filterHidden(it, showHidden) }
                    ?.let { FileUtils.applySort(it, config) }
                    ?: emptyList()
            }
        }

    suspend fun recent(limit: Int = 100): List<FileItem> = withContext(Dispatchers.IO) {
        FileUtils.recentFiles(context, limit)
    }

    fun breadcrumbs(root: File, current: File): List<BreadcrumbSegment> =
        FileUtils.buildBreadcrumbs(root, current)

    fun resolveRoot(uri: Uri): File? = roots().firstOrNull { it.uri == uri }?.file

    /**
     * 检查路径是否敏感，需要警告确认。
     */
    fun isSensitivePath(path: String): Boolean = shizukuManager.isSensitivePath(path)

    suspend fun delete(items: List<FileItem>, isShizuku: Boolean = false): Int = withContext(Dispatchers.IO) {
        var n = 0
        items.forEach { item ->
            val f = File(item.path)
            val deleted = if (isShizuku && shizukuManager.canAccess(f.absolutePath)) {
                shizukuManager.delete(f.absolutePath)
            } else {
                f.exists() && f.deleteRecursively()
            }
            if (deleted) n++
        }
        n
    }

    suspend fun rename(item: FileItem, newName: String, isShizuku: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val f = File(item.path)
        val target = File(f.parentFile, newName)
        if (target.exists()) return@withContext false
        if (isShizuku && shizukuManager.canAccess(f.absolutePath)) {
            shizukuManager.rename(f.absolutePath, newName)
        } else {
            f.renameTo(target)
        }
    }

    suspend fun createFolder(parent: File, name: String, isShizuku: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val target = File(parent, name)
        if (target.exists()) return@withContext false
        if (isShizuku && shizukuManager.canAccess(parent.absolutePath)) {
            shizukuManager.createDirectory(target.absolutePath)
        } else {
            target.mkdirs()
        }
    }

    suspend fun createFile(parent: File, name: String, isShizuku: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val target = File(parent, name)
        if (target.exists()) return@withContext false
        if (isShizuku && shizukuManager.canAccess(parent.absolutePath)) {
            shizukuManager.createFile(target.absolutePath)
        } else {
            target.createNewFile()
        }
    }

    suspend fun deleteSingle(item: FileItem, isShizuku: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val f = File(item.path)
        val deleted = if (isShizuku && shizukuManager.canAccess(f.absolutePath)) {
            shizukuManager.delete(f.absolutePath)
        } else {
            f.exists() && f.deleteRecursively()
        }
        deleted
    }
}

data class StorageRoot(
    val displayName: String,
    val uri: Uri,
    val file: File?,         // null 表示 SAF 根
    val isSaf: Boolean = false,
    val isShizuku: Boolean = false,
)
