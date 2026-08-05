package wanjie.quicklook.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.compose.runtime.Immutable
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import kotlin.math.ln
import kotlin.math.pow

/**
 * Pure file-system helpers. No Android framework state stored here.
 */
object FileUtils {

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return "%.1f %s".format(value, units[digitGroups])
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return "—"
        val fmt = SimpleDateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return fmt.format(java.util.Date(timestamp))
    }

    /** 格式化毫秒时长为 mm:ss 或 h:mm:ss */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000L
        val s = totalSec % 60L
        val m = (totalSec / 60L) % 60L
        val h = totalSec / 3600L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg", "tiff", "ico", "raw")
    private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "m4v", "wmv", "mpeg", "mpg")
    private val audioExt = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma", "mid", "midi", "amr")
    private val textExt = setOf("txt", "md", "log", "csv", "rtf", "json", "xml", "yaml", "yml", "ini", "conf", "properties")
    private val docExt = setOf("doc", "docx", "odt", "pages")
    private val sheetExt = setOf("xls", "xlsx", "ods", "numbers", "csv")
    private val slideExt = setOf("ppt", "pptx", "odp", "key")
    private val pdfExt = setOf("pdf")
    private val codeExt = setOf("kt", "java", "py", "js", "ts", "html", "css", "cpp", "c", "h", "rs", "go", "rb", "php", "swift", "sh", "bat", "sql", "gradle", "dart")
    private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
    private val appExt = setOf("apk", "apks", "xapk", "aab")
    private val ebookExt = setOf("epub", "mobi", "azw", "azw3", "fb2", "djvu")
    private val fontExt = setOf("ttf", "otf", "woff", "woff2")

    fun categoryFor(name: String, isDirectory: Boolean, mimeType: String): FileCategory {
        if (isDirectory) return FileCategory.FOLDER
        // MIME 前缀优先于扩展名匹配
        return when {
            mimeType.startsWith("image/") -> FileCategory.IMAGE
            mimeType.startsWith("video/") -> FileCategory.VIDEO
            mimeType.startsWith("audio/") -> FileCategory.AUDIO
            mimeType.startsWith("text/") -> FileCategory.TEXT
            else -> quickCategoryFor(name, isDirectory = false)
        }
    }

    /**
     * 仅靠扩展名快速判断分类，跳过 MIME 查询。
     * 用于搜索等高性能场景，速度比 [categoryFor] 快得多。
     * 未知扩展名返回 [FileCategory.OTHER]。
     */
    fun quickCategoryFor(name: String, isDirectory: Boolean): FileCategory {
        if (isDirectory) return FileCategory.FOLDER
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in imageExt -> FileCategory.IMAGE
            in videoExt -> FileCategory.VIDEO
            in audioExt -> FileCategory.AUDIO
            in pdfExt -> FileCategory.PDF
            in docExt -> FileCategory.DOCUMENT
            in sheetExt -> FileCategory.SPREADSHEET
            in slideExt -> FileCategory.PRESENTATION
            in codeExt -> FileCategory.CODE
            in ebookExt -> FileCategory.EBOOK
            in fontExt -> FileCategory.FONT
            in archiveExt -> FileCategory.ARCHIVE
            in appExt -> FileCategory.APP
            in textExt -> FileCategory.TEXT
            else -> FileCategory.OTHER
        }
    }

    fun mimeTypeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /**
     * 构建打开文件的 Intent。使用 FileProvider 提供 content Uri。
     * 返回 null 表示无法构建（文件不存在等）。
     */
    fun buildOpenIntent(context: Context, item: FileItem): Intent? {
        val file = File(item.path)
        if (!file.exists() || !file.isFile) return null
        return try {
            val uri = buildContentUri(context, file) ?: return null
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "buildOpenIntent failed", e)
            null
        }
    }

    /**
     * 通过 FileProvider 为文件生成 content:// URI。
     * 内部播放器/查看器统一使用此 URI，避免 file:// 的安全限制。
     * 返回 null 表示 FileProvider 无法处理该路径。
     */
    fun buildContentUri(context: Context, file: File): Uri? {
        if (!file.exists()) return null
        return try {
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            Log.e("FileUtils", "buildContentUri failed", e)
            null
        }
    }

    /** 重载：按路径生成 content URI */
    fun buildContentUri(context: Context, path: String): Uri? = buildContentUri(context, File(path))

    /** Lists files in [dir], returns null if not readable. */
    fun listFiles(dir: File): List<FileItem>? {
        if (!dir.exists() || !dir.isDirectory) return null
        val children = dir.listFiles() ?: return null
        // 不在列表阶段计算子目录文件数——那是 N 次额外 I/O，会严重拖慢大目录加载。
        return children.map { f ->
            val name = f.name
            val mime = if (f.isDirectory) "inode/directory" else mimeTypeFor(name)
            FileItem(
                uri = f.toUri(),
                name = name,
                path = f.absolutePath,
                isDirectory = f.isDirectory,
                size = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified(),
                mimeType = mime,
                category = categoryFor(name, f.isDirectory, mime),
                childCount = 0,
            )
        }
    }

    fun applySort(items: List<FileItem>, config: SortConfig): List<FileItem> {
        val cmp = Comparator<FileItem> { a, b ->
            if (config.foldersFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            when (config.order) {
                SortOrder.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                SortOrder.NAME_DESC -> b.name.compareTo(a.name, ignoreCase = true)
                SortOrder.MODIFIED_DESC -> b.lastModified.compareTo(a.lastModified)
                SortOrder.MODIFIED_ASC -> a.lastModified.compareTo(b.lastModified)
                SortOrder.SIZE_DESC -> b.size.compareTo(a.size)
                SortOrder.SIZE_ASC -> a.size.compareTo(b.size)
                SortOrder.TYPE -> a.mimeType.compareTo(b.mimeType).let { if (it == 0) a.name.compareTo(b.name, true) else it }
            }
        }
        return items.sortedWith(cmp)
    }

    fun filterHidden(items: List<FileItem>, showHidden: Boolean): List<FileItem> =
        if (showHidden) items else items.filterNot { it.name.startsWith(".") }

    fun buildBreadcrumbs(root: File, current: File): List<BreadcrumbSegment> {
        val segs = mutableListOf<BreadcrumbSegment>()
        var f: File? = current
        while (f != null && f.canonicalPath.startsWith(root.canonicalPath)) {
            segs.add(0, BreadcrumbSegment(if (f == root) root.name else f.name, f.toUri()))
            if (f == root) break
            f = f.parentFile
        }
        if (segs.isEmpty()) segs.add(BreadcrumbSegment(root.name, root.toUri()))
        return segs
    }

    /**
     * Query recent files via MediaStore. Returns newest first, capped at [limit].
     */
    fun recentFiles(context: Context, limit: Int = 100): List<FileItem> {
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        val out = mutableListOf<FileItem>()
        val coll = MediaStore.Files.getContentUri("external")
        val bundle = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        context.contentResolver.query(coll, proj, bundle, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val mime = c.getString(mimeCol) ?: "application/octet-stream"
                val path = c.getString(dataCol) ?: ""
                val uri = if (path.isNotEmpty()) path.toUri() else {
                    val id = c.getLong(idCol)
                    Uri.withAppendedPath(coll, id.toString())
                }
                out.add(FileItem(
                    uri = uri,
                    name = name,
                    path = path,
                    isDirectory = false,
                    size = c.getLong(sizeCol),
                    lastModified = c.getLong(modCol) * 1000L,
                    mimeType = mime,
                    category = categoryFor(name, false, mime),
                ))
            }
        }
        return out
    }

    /**
     * 从音频文件中一次性提取元数据（标题、歌手、专辑）与内嵌封面。
     * 任一字段缺失时对应字段为 null。调用方应在 IO 线程执行。
     */
    fun extractAudioMetadata(context: Context, uriOrPath: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            applyDataSource(retriever, context, uriOrPath)
            AudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
                artwork = retriever.embeddedPicture,
            )
        } catch (e: Exception) {
            Log.e("FileUtils", "extractAudioMetadata failed", e)
            AudioMetadata(null, null, null, null)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun applyDataSource(retriever: MediaMetadataRetriever, context: Context, uriOrPath: String) {
        if (uriOrPath.startsWith("content://")) {
            retriever.setDataSource(context, Uri.parse(uriOrPath))
        } else {
            val p = if (uriOrPath.startsWith("file://")) uriOrPath.removePrefix("file://") else uriOrPath
            retriever.setDataSource(p)
        }
    }

    /**
     * 解码图片字节数组为 [Bitmap]，按 [maxDim] 降采样以控制内存占用。
     * 解码失败返回 null。
     */
    fun decodeSampledBitmap(data: ByteArray, maxDim: Int = 512): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }

    /**
     * 解析 APK 文件的基本信息：应用名、包名、版本、SDK、权限与图标。
     * 调用方应在 IO 线程执行。解析失败返回 null。
     */
    fun parseApk(context: Context, path: String): ApkInfo? {
        val pm = context.packageManager
        val pkg = pm.getPackageArchiveInfo(path, PackageManager.GET_PERMISSIONS) ?: return null
        val appInfo = pkg.applicationInfo ?: return null
        // getPackageArchiveInfo 不会设置 sourceDir/publicSourceDir，loadLabel/loadIcon 依赖它们，必须手动赋值
        appInfo.sourceDir = path
        appInfo.publicSourceDir = path
        val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode else pkg.versionCode.toLong()
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 1
        return ApkInfo(
            label = runCatching { appInfo.loadLabel(pm)?.toString() }.getOrNull(),
            packageName = pkg.packageName,
            versionName = pkg.versionName,
            versionCode = verCode,
            minSdk = minSdk,
            targetSdk = appInfo.targetSdkVersion,
            permissions = pkg.requestedPermissions?.toList() ?: emptyList(),
            icon = runCatching { appInfo.loadIcon(pm) }.getOrNull(),
        )
    }

    /**
     * 轻量级加载 APK 图标，仅获取 Drawable，跳过权限等额外解析。
     * 用于文件列表项的图标显示。调用方应在 IO 线程执行。
     */
    fun loadApkIcon(context: Context, path: String): Drawable? {
        val pm = context.packageManager
        val pkg = pm.getPackageArchiveInfo(path, 0) ?: return null
        val appInfo = pkg.applicationInfo ?: return null
        // 同上：必须手动设置 sourceDir/publicSourceDir，否则 loadIcon 返回默认系统图标或失败
        appInfo.sourceDir = path
        appInfo.publicSourceDir = path
        return runCatching { appInfo.loadIcon(pm) }.getOrNull()
    }
}

/**
 * 音频文件元数据：标题、歌手、专辑与内嵌封面字节。
 * 任一字段缺失时为 null，由 UI 层负责回退到文件名。
 */
@Immutable
data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioMetadata) return false
        return title == other.title && artist == other.artist && album == other.album
    }
    override fun hashCode(): Int {
        var r = title?.hashCode() ?: 0
        r = 31 * r + (artist?.hashCode() ?: 0)
        r = 31 * r + (album?.hashCode() ?: 0)
        return r
    }
}

/**
 * APK 安装包解析结果：应用名、包名、版本、SDK 要求与权限列表。
 * icon 为系统加载的 Drawable，由 UI 层转换为可渲染位图。
 */
@Immutable
data class ApkInfo(
    val label: String?,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val icon: Drawable?,
)
