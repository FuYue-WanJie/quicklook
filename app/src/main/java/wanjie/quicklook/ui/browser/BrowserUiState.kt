package wanjie.quicklook.ui.browser

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import wanjie.quicklook.data.Bookmark
import wanjie.quicklook.data.BreadcrumbSegment
import wanjie.quicklook.data.FileItem
import wanjie.quicklook.data.SortConfig
import wanjie.quicklook.data.StorageRoot

enum class BrowserLoadState { Loading, Ready, NeedsPermission, Error }

/** 当前弹出的对话框 */
sealed interface BrowserDialog {
    data object None : BrowserDialog
    data object NewFolder : BrowserDialog
    data object NewFile : BrowserDialog
    data class Rename(val item: FileItem) : BrowserDialog
    data class Details(val item: FileItem) : BrowserDialog
    data class JumpToPath(val initial: String) : BrowserDialog
    /** 移除书签确认：[name] 用于文案，[id] 用于定位 */
    data class RemoveBookmark(val name: String, val id: String) : BrowserDialog
}

/** 搜索范围 */
enum class SearchScope { ALL, FILES_ONLY, FOLDERS_ONLY }

/**
 * Immutable UI state for the browser screen.
 */
@Immutable
data class BrowserUiState(
    val loadState: BrowserLoadState = BrowserLoadState.Loading,
    val currentRoot: StorageRoot? = null,
    val currentPath: String = "",
    /** 当前路径是否为 SAF 模式（currentRoot.isSaf） */
    val isSafMode: Boolean = false,
    val breadcrumbs: List<BreadcrumbSegment> = emptyList(),
    val items: List<FileItem> = emptyList(),
    val filteredItems: List<FileItem> = emptyList(),
    val searchQuery: String = "",
    val sortConfig: SortConfig = SortConfig(),
    val showHidden: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedUris: Set<Uri> = emptySet(),
    val showSortSheet: Boolean = false,
    val roots: List<StorageRoot> = emptyList(),
    val itemCount: Int = 0,
    val errorMessage: String? = null,
    val dialog: BrowserDialog = BrowserDialog.None,
    // ---- 书签 ----
    val bookmarks: List<Bookmark> = emptyList(),
    /** 当前目录是否已加入书签（仅文件路径模式有效） */
    val currentPathIsBookmarked: Boolean = false,
    val currentBookmarkName: String = "",
    // ---- 搜索模式 ----
    val searchMode: Boolean = false,
    val searchScope: SearchScope = SearchScope.ALL,
    val useRegex: Boolean = false,
    val searchResults: List<FileItem> = emptyList(),
    val searching: Boolean = false,
) {
    val selectedCount: Int get() = selectedUris.size
}

/**
 * One-time effect events to be consumed by the UI.
 */
@Stable
sealed interface BrowserEvent {
    data class ShowSnackbar(val message: String) : BrowserEvent
    data class OpenImage(val path: String, val name: String) : BrowserEvent
    data class OpenVideo(val path: String, val name: String) : BrowserEvent
    data class OpenAudio(val path: String, val name: String) : BrowserEvent
    data class OpenText(val path: String, val name: String) : BrowserEvent
    data class OpenArchive(val path: String, val name: String) : BrowserEvent
}
