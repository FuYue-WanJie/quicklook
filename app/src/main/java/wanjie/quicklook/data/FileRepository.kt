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

    /** Roots shown on the Storage tab. */
    fun roots(): List<StorageRoot> = listOf(
        StorageRoot("内部存储", externalRoot.toUri(), externalRoot),
        StorageRoot("下载", downloadsRoot.toUri(), downloadsRoot),
        StorageRoot("应用文件", appRoot.toUri(), appRoot),
    )

    suspend fun listDirectory(dir: File, config: SortConfig, showHidden: Boolean): List<FileItem> =
        withContext(Dispatchers.IO) {
            val raw = FileUtils.listFiles(dir).orEmpty()
            val filtered = FileUtils.filterHidden(raw, showHidden)
            FileUtils.applySort(filtered, config)
        }

    suspend fun recent(limit: Int = 100): List<FileItem> = withContext(Dispatchers.IO) {
        FileUtils.recentFiles(context, limit)
    }

    fun breadcrumbs(root: File, current: File): List<BreadcrumbSegment> =
        FileUtils.buildBreadcrumbs(root, current)

    fun resolveRoot(uri: Uri): File? = roots().firstOrNull { it.uri == uri }?.file

    suspend fun delete(items: List<FileItem>): Int = withContext(Dispatchers.IO) {
        var n = 0
        items.forEach { item ->
            val f = File(item.path)
            if (f.exists() && f.deleteRecursively()) n++
        }
        n
    }

    suspend fun rename(item: FileItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        val f = File(item.path)
        val target = File(f.parentFile, newName)
        if (target.exists()) return@withContext false
        f.renameTo(target)
    }

    suspend fun createFolder(parent: File, name: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(parent, name)
        if (target.exists()) return@withContext false
        target.mkdirs()
    }

    suspend fun createFile(parent: File, name: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(parent, name)
        if (target.exists()) return@withContext false
        target.createNewFile()
    }

    suspend fun deleteSingle(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        val f = File(item.path)
        f.exists() && f.deleteRecursively()
    }
}

data class StorageRoot(
    val displayName: String,
    val uri: Uri,
    val file: File?,         // null 表示 SAF 根
    val isSaf: Boolean = false,
)
