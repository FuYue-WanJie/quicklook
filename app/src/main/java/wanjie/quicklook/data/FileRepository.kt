package wanjie.quicklook.data

import android.os.Environment
import java.io.File

/** 文件类型分类，用于图标与文案。 */
enum class FileCategory {
    FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, CODE, TEXT, OTHER
}

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val category: FileCategory,
    val childCount: Int = 0,
)

object FileRepository {

    /** 默认起始目录：优先外部存储根目录，不可用则回退到应用专属目录。 */
    fun defaultRoot(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext.exists() && ext.canRead()) ext
        else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .takeIf { it.exists() } ?: ext
    }

    fun parent(file: File): File? = file.parentFile?.takeIf { it.canRead() }

    /** 列目录尝试结果：[ListAttempt.Ok] 表示可访问（含空目录），[ListAttempt.Denied] 表示无权限/无法读取。 */
    sealed interface ListAttempt {
        data class Ok(val entries: List<FileEntry>) : ListAttempt
        data object Denied : ListAttempt
    }

    /**
     * 列目录并区分「无权限」与「空目录」。
     * - listFiles() 返回 null（非目录或 IO 异常，Android 11+ 下 Android/data 即表现为此）→ Denied
     * - 返回非 null 但为空 → Ok(emptyList())
     */
    fun tryList(dir: File, showHidden: Boolean = false): ListAttempt {
        val files = dir.listFiles() ?: return ListAttempt.Denied
        // 先过滤再转换，减少对象创建；目录优先、名称升序。
        val entries = files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { it.toEntry() }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
        return ListAttempt.Ok(entries)
    }

    /**
     * 仅做单次元数据读取，不再对子目录调用 listFiles() 统计子项数，
     * 避免大目录下 N 次额外磁盘 IO 导致的卡顿。
     */
    private fun File.toEntry(): FileEntry = FileEntry(
        name = name,
        path = absolutePath,
        isDirectory = isDirectory,
        size = if (isDirectory) 0L else length(),
        lastModified = lastModified(),
        category = categorize(this),
        childCount = 0,
    )

    private fun categorize(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.FOLDER
        val ext = file.extension.lowercase()
        return when (ext) {
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> FileCategory.IMAGE
            "mp4", "mkv", "avi", "mov", "webm", "3gp" -> FileCategory.VIDEO
            "mp3", "flac", "aac", "ogg", "wav", "m4a" -> FileCategory.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> FileCategory.DOCUMENT
            "zip", "rar", "7z", "tar", "gz" -> FileCategory.ARCHIVE
            "apk", "xapk", "apks" -> FileCategory.APK
            "kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "go", "c", "cpp" -> FileCategory.CODE
            "txt", "md", "log" -> FileCategory.TEXT
            else -> FileCategory.OTHER
        }
    }

    // ---- 内置编辑器/查看器支持 ----

    /** 可在内置文本编辑器打开的扩展名。 */
    private val EDITABLE_EXTS = setOf(
        "txt", "md", "log", "json", "xml", "html", "css", "csv", "kt", "java", "py", "js", "ts", "go", "c", "cpp", "sh", "properties", "yml", "yaml",
    )

    /** 可在内置图片查看器打开的扩展名。 */
    private val VIEWABLE_IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    /** 是否可用内置文本编辑器打开。 */
    fun isEditableText(file: File): Boolean =
        !file.isDirectory && file.extension.lowercase() in EDITABLE_EXTS

    /** 是否可用内置图片查看器打开。 */
    fun isViewableImage(file: File): Boolean =
        !file.isDirectory && file.extension.lowercase() in VIEWABLE_IMAGE_EXTS

    /** 读取文本文件内容（UTF-8），失败返回 null。 */
    fun readText(file: File): String? = runCatching {
        if (!file.canRead()) return null
        file.readText(Charsets.UTF_8)
    }.getOrNull()

    /** 覆盖写入文本内容（UTF-8），成功返回 true。 */
    fun writeText(file: File, text: String): Boolean = runCatching {
        file.writeText(text, Charsets.UTF_8)
        true
    }.getOrDefault(false)
}
