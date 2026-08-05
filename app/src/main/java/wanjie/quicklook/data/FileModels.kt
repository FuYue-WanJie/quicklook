package wanjie.quicklook.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * 文件分类，用于图标/色调映射与分类页。
 */
enum class FileCategory {
    FOLDER, IMAGE, VIDEO, AUDIO,
    DOCUMENT, SPREADSHEET, PRESENTATION, TEXT, CODE, PDF,
    ARCHIVE, APP, EBOOK, FONT, OTHER
}

/**
 * Immutable file/folder descriptor for the UI layer.
 */
@Immutable
data class FileItem(
    val uri: Uri,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val category: FileCategory,
    val childCount: Int = 0,
)

/**
 * Sort options exposed in the UI.
 */
@Stable
enum class SortOrder {
    NAME_ASC, NAME_DESC,
    MODIFIED_DESC, MODIFIED_ASC,
    SIZE_DESC, SIZE_ASC,
    TYPE,
}

/**
 * Sort configuration. [foldersFirst] is a separate toggle per MD3 expression pattern.
 */
@Immutable
data class SortConfig(
    val order: SortOrder = SortOrder.NAME_ASC,
    val foldersFirst: Boolean = true,
)

/**
 * Breadcrumb segment for the current navigation path.
 */
@Immutable
data class BreadcrumbSegment(
    val name: String,
    val uri: Uri,
)
