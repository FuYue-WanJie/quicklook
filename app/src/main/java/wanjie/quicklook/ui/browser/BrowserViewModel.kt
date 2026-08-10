package wanjie.quicklook.ui.browser

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import rikka.shizuku.Shizuku
import wanjie.quicklook.QuickLookApp
import wanjie.quicklook.R
import wanjie.quicklook.data.Bookmark
import wanjie.quicklook.data.FileCategory
import wanjie.quicklook.data.FileItem
import wanjie.quicklook.data.FileRepository
import wanjie.quicklook.data.FileUtils
import wanjie.quicklook.data.SettingsStore
import wanjie.quicklook.data.ShizukuManager
import wanjie.quicklook.data.SortConfig
import wanjie.quicklook.data.StorageRoot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val MAX_SEARCH_DEPTH = 12
        const val MAX_RESULTS = 500
        const val BATCH_SIZE = 50
    }

    private val repo = FileRepository(application)
    private val settings = SettingsStore(application)
    private val app = application as QuickLookApp
    private val bookmarkStore = app.bookmarkStore
    private val safManager = app.safManager
    private val shizukuManager = ShizukuManager(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // 搜索 Job：用于防抖取消上一次未完成的搜索
    private var searchJob: Job? = null

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                viewModelScope.launch {
                    snack(R.string.snack_shizuku_granted)
                    val roots = repo.roots()
                    _uiState.update { it.copy(roots = roots) }
                    if (_uiState.value.currentRoot == null) {
                        val shizukuRoot = roots.firstOrNull { it.isShizuku }
                        if (shizukuRoot != null) refresh(shizukuRoot, null)
                    }
                }
            } else {
                viewModelScope.launch {
                    snack(R.string.snack_shizuku_denied)
                }
            }
        }
    }

    /** 发送 Snackbar 事件的小工具，避免重复 getApplication().getString 样板 */
    private fun snack(resId: Int, vararg args: Any) {
        viewModelScope.launch {
            _events.send(BrowserEvent.ShowSnackbar(getApplication<Application>().getString(resId, *args)))
        }
    }

    /** SAF 失效提示的主体应用名：优先取 provider 应用名，解析失败时用通用名 */
    private fun safProviderName(treeUri: String): String {
        return safManager.providerName(treeUri)
            ?: getApplication<Application>().getString(R.string.snack_saf_provider_fallback)
    }

    /** 关闭对话框并刷新当前目录 */
    private suspend fun dismissAndRefresh() {
        val state = _uiState.value
        _uiState.update { it.copy(dialog = BrowserDialog.None) }
        refresh(state.currentRoot ?: return, state.currentPath.ifBlank { null })
    }

    /** 检查是否有存储访问权限 */
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val ctx = getApplication<Application>()
            ctx.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    init {
        val roots = repo.roots()
        _uiState.update { it.copy(roots = roots) }

        // 注册 Shizuku 权限结果监听
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        if (checkPermission()) {
            _uiState.update { it.copy(loadState = BrowserLoadState.Loading) }
            viewModelScope.launch { refresh(roots.first(), null) }
        } else {
            _uiState.update { it.copy(loadState = BrowserLoadState.NeedsPermission) }
        }

        viewModelScope.launch {
            combine(settings.sortConfig, settings.showHidden) { sort, hidden -> sort to hidden }
                .collect { (sort, hidden) ->
                    val current = _uiState.value
                    _uiState.update { it.copy(sortConfig = sort, showHidden = hidden) }
                    if (current.currentRoot != null && current.loadState != BrowserLoadState.NeedsPermission) {
                        refresh(current.currentRoot, current.currentPath.ifBlank { null })
                    }
                }
        }

        // 订阅书签列表：更新 UI + 同步当前目录是否已加书签
        viewModelScope.launch {
            bookmarkStore.bookmarks.collect { list ->
                val current = _uiState.value
                val matched = matchedBookmark(current, list)
                _uiState.update {
                    it.copy(
                        bookmarks = list,
                        currentPathIsBookmarked = matched != null,
                        currentBookmarkName = matched?.name ?: "",
                    )
                }
            }
        }
    }

    /** 找到当前目录对应的书签（仅文件路径模式） */
    private fun matchedBookmark(state: BrowserUiState, list: List<Bookmark>): Bookmark? {
        if (state.isSafMode || state.currentPath.isBlank()) return null
        return list.firstOrNull { !it.isSaf && it.path == state.currentPath }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _uiState.update { it.copy(loadState = BrowserLoadState.Loading) }
            viewModelScope.launch {
                snack(R.string.snack_granted)
                refresh(_uiState.value.roots.first(), null)
            }
        } else {
            _uiState.update { it.copy(loadState = BrowserLoadState.NeedsPermission) }
            snack(R.string.snack_denied)
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun checkShizukuPermission(): Boolean {
        return shizukuManager.isGranted
    }

    val shizukuAvailable: Boolean get() = shizukuManager.isAvailable
    val shizukuGranted: Boolean get() = shizukuManager.isGranted

    fun selectRoot(root: StorageRoot) = viewModelScope.launch {
        refresh(root, null)
    }

    fun openDirectory(item: FileItem) = viewModelScope.launch {
        if (!item.isDirectory) return@launch
        val root = _uiState.value.currentRoot ?: return@launch
        if (root.isSaf) {
            // SAF 模式：item.path 为子文档 URI，直接刷新
            refresh(root, item.path)
        } else {
            refresh(root, item.path)
        }
    }

    fun navigateToBreadcrumb(uri: Uri) = viewModelScope.launch {
        val root = _uiState.value.currentRoot ?: return@launch
        if (root.isSaf) {
            // SAF 模式：uri 是 document URI
            refresh(root, uri.toString())
        } else {
            val path = uri.path ?: return@launch
            refresh(root, path)
        }
    }

    fun goUp(): Boolean {
        val state = _uiState.value
        val root = state.currentRoot ?: return false
        val crumbs = state.breadcrumbs
        if (crumbs.size <= 1) return false
        val target = crumbs[crumbs.size - 2]
        viewModelScope.launch {
            if (root.isSaf) refresh(root, target.uri.toString())
            else refresh(root, target.uri.path)
        }
        return true
    }

    // ---- 跳转目录 ----
    fun showJumpDialog() {
        _uiState.update { it.copy(dialog = BrowserDialog.JumpToPath(it.currentPath)) }
    }

    fun jumpToPath(path: String) = viewModelScope.launch {
        val root = _uiState.value.currentRoot ?: return@launch
        if (root.isSaf) {
            snack(R.string.snack_jump_saf_unsupported)
            return@launch
        }
        val dir = File(path.trim())
        if (!dir.exists() || !dir.isDirectory) {
            snack(R.string.snack_jump_invalid)
            return@launch
        }
        _uiState.update { it.copy(dialog = BrowserDialog.None) }
        refresh(root, dir.absolutePath)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (_uiState.value.searchMode) {
            scheduleSearch()
        } else {
            applySearchFilter()
        }
    }

    // ---- 搜索模式 ----
    fun enterSearchMode() {
        _uiState.update { it.copy(searchMode = true, searchResults = emptyList()) }
    }

    fun exitSearchMode() {
        resetSearch()
    }

    private fun resetSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchMode = false,
                searchQuery = "",
                searchResults = emptyList(),
                searching = false,
            )
        }
    }

    fun setSearchScope(scope: SearchScope) {
        _uiState.update { it.copy(searchScope = scope) }
        scheduleSearch()
    }

    fun setUseRegex(enabled: Boolean) {
        _uiState.update { it.copy(useRegex = enabled) }
        scheduleSearch()
    }

    /** 防抖触发搜索：300ms 内连续输入只搜一次 */
    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch()
        }
    }

    private suspend fun performSearch() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        val root = state.currentRoot ?: return
        if (root.isSaf) {
            snack(R.string.snack_search_saf_unsupported)
            return
        }
        val scope = state.searchScope
        val useRegex = state.useRegex
        val showHidden = state.showHidden
        _uiState.update { it.copy(searching = true, searchResults = emptyList()) }

        val regex: Regex? = if (useRegex) {
            try { Regex(query, RegexOption.IGNORE_CASE) } catch (e: Exception) { null }
        } else null
        if (useRegex && regex == null) {
            snack(R.string.search_regex_invalid)
        }

        val results = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val out = mutableListOf<FileItem>()
            val queue = ArrayDeque<Pair<File, Int>>()
            queue.addLast((root.file ?: return@withContext out) to 0)
            var batchSize = 0
            while (queue.isNotEmpty()) {
                if (out.size >= MAX_RESULTS) break
                val (cur, depth) = queue.removeFirst()
                if (depth > MAX_SEARCH_DEPTH) continue
                val children = cur.listFiles() ?: continue
                for (f in children) {
                    if (out.size >= MAX_RESULTS) break
                    if (!showHidden && f.name.startsWith(".")) continue
                    val matches = if (regex != null) {
                        regex.containsMatchIn(f.name)
                    } else {
                        f.name.contains(query, ignoreCase = true)
                    }
                    if (matches) {
                        val accept = when (scope) {
                            SearchScope.ALL -> true
                            SearchScope.FILES_ONLY -> f.isFile
                            SearchScope.FOLDERS_ONLY -> f.isDirectory
                        }
                        if (accept) {
                            val cat = FileUtils.quickCategoryFor(f.name, f.isDirectory)
                            out.add(
                                FileItem(
                                    uri = f.toUri(),
                                    name = f.name,
                                    path = f.absolutePath,
                                    isDirectory = f.isDirectory,
                                    size = if (f.isFile) f.length() else 0L,
                                    lastModified = f.lastModified(),
                                    mimeType = "application/octet-stream",
                                    category = cat,
                                )
                            )
                            batchSize++
                        }
                    }
                    if (f.isDirectory && f.canRead() && depth < MAX_SEARCH_DEPTH) {
                        queue.addLast(f to (depth + 1))
                    }
                }
                if (batchSize >= BATCH_SIZE) {
                    _uiState.update { it.copy(searchResults = out.toList()) }
                    batchSize = 0
                    yield()
                }
            }
            yield()
            out
        }
        val truncated = results.size >= MAX_RESULTS
        _uiState.update { it.copy(searchResults = results, searching = false) }
        if (truncated) {
            snack(R.string.search_truncated, MAX_RESULTS)
        }
    }

    fun navigateToPath(targetPath: String) = viewModelScope.launch {
        val root = _uiState.value.currentRoot ?: return@launch
        if (root.isSaf) {
            // SAF 模式：targetPath 是 document URI
            resetSearch()
            refresh(root, targetPath)
            return@launch
        }
        val file = File(targetPath)
        val dir = if (file.isDirectory) file else file.parentFile
        if (dir == null || !dir.exists() || !dir.isDirectory) {
            snack(R.string.snack_error_open_folder)
            return@launch
        }
        resetSearch()
        refresh(root, dir.absolutePath)
    }

    fun setShowHidden(enabled: Boolean) {
        viewModelScope.launch {
            settings.setShowHidden(enabled)
            _uiState.update { it.copy(showHidden = enabled) }
            refresh(_uiState.value.currentRoot ?: return@launch, _uiState.value.currentPath.ifBlank { null })
        }
    }

    fun toggleSortSheet() {
        _uiState.update { it.copy(showSortSheet = !it.showSortSheet) }
    }

    fun setSortConfig(config: SortConfig) {
        viewModelScope.launch {
            settings.setSortConfig(config)
            _uiState.update { it.copy(showSortSheet = false) }
        }
    }

    fun onItemClicked(item: FileItem) {
        val state = _uiState.value
        if (state.selectionMode) {
            toggleSelection(item)
        } else if (item.isDirectory) {
            openDirectory(item)
        } else {
            openFile(item)
        }
    }

    fun onItemLongClicked(item: FileItem) {
        if (!_uiState.value.selectionMode) {
            _uiState.update { it.copy(selectionMode = true, selectedUris = setOf(item.uri)) }
        } else {
            toggleSelection(item)
        }
    }

    fun toggleSelection(item: FileItem) {
        _uiState.update { state ->
            val newSel = state.selectedUris.toMutableSet().apply {
                if (!add(item.uri)) remove(item.uri)
            }
            state.copy(selectedUris = newSel, selectionMode = newSel.isNotEmpty())
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedUris = state.filteredItems.map { it.uri }.toSet(), selectionMode = true)
        }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val current = state.selectedUris
            val inverted = state.filteredItems.map { it.uri }.filterNot { it in current }.toSet()
            state.copy(selectedUris = inverted, selectionMode = inverted.isNotEmpty())
        }
    }

    fun exitSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedUris = emptySet()) }
    }

    fun deleteSelected() = viewModelScope.launch {
        val state = _uiState.value
        if (state.isShizukuMode && repo.isSensitivePath(state.currentPath)) {
            val count = state.selectedUris.size
            _uiState.update { it.copy(dialog = BrowserDialog.ShizukuWarning(
                action = ShizukuAction.DELETE_MULTI,
                targetName = "共 $count 个文件/文件夹",
                detail = "选中项",
            )) }
        } else {
            val items = state.items.filter { it.uri in state.selectedUris }
            val n = repo.delete(items, state.isShizukuMode)
            snack(R.string.snack_deleted, n)
            exitSelection()
            refresh(state.currentRoot ?: return@launch, state.currentPath.ifBlank { null })
        }
    }

    fun showNewFolderDialog() {
        val state = _uiState.value
        if (state.isShizukuMode && repo.isSensitivePath(state.currentPath)) {
            _uiState.update { it.copy(dialog = BrowserDialog.ShizukuWarning(
                action = ShizukuAction.CREATE_FOLDER,
                targetName = "",
                detail = state.currentPath,
            )) }
        } else {
            _uiState.update { it.copy(dialog = BrowserDialog.NewFolder) }
        }
    }

    fun confirmCreateFolder(name: String) = createEntry(
        isFolder = true,
        name = name,
        okSnack = R.string.snack_folder_created,
        failSnack = R.string.snack_folder_create_failed,
    )

    fun showNewFileDialog() {
        val state = _uiState.value
        if (state.isShizukuMode && repo.isSensitivePath(state.currentPath)) {
            _uiState.update { it.copy(dialog = BrowserDialog.ShizukuWarning(
                action = ShizukuAction.CREATE_FILE,
                targetName = "",
                detail = state.currentPath,
            )) }
        } else {
            _uiState.update { it.copy(dialog = BrowserDialog.NewFile) }
        }
    }

    fun confirmCreateFile(name: String) = createEntry(
        isFolder = false,
        name = name,
        okSnack = R.string.snack_file_created,
        failSnack = R.string.snack_file_create_failed,
    )

    private fun createEntry(isFolder: Boolean, name: String, okSnack: Int, failSnack: Int, isShizuku: Boolean = false) = viewModelScope.launch {
        val state = _uiState.value
        val root = state.currentRoot ?: return@launch
        if (root.isSaf) {
            snack(R.string.snack_saf_readonly)
            return@launch
        }
        val dir = File(state.currentPath.ifBlank { root.file?.absolutePath ?: return@launch })
        val ok = if (isFolder) repo.createFolder(dir, name, isShizuku) else repo.createFile(dir, name, isShizuku)
        if (ok) snack(okSnack, name) else snack(failSnack)
        dismissAndRefresh()
    }

    fun showRenameDialog(item: FileItem) {
        val state = _uiState.value
        if (state.isShizukuMode && repo.isSensitivePath(item.path)) {
            _uiState.update { it.copy(dialog = BrowserDialog.ShizukuWarning(
                action = ShizukuAction.RENAME,
                targetName = item.name,
                detail = item.path,
            )) }
        } else {
            _uiState.update { it.copy(dialog = BrowserDialog.Rename(item)) }
        }
    }

    fun confirmRename(newName: String) = viewModelScope.launch {
        val state = _uiState.value
        val warning = state.dialog as? BrowserDialog.ShizukuWarning
        if (warning != null) {
            _uiState.update { it.copy(dialog = BrowserDialog.None) }
            val item = state.items.firstOrNull { it.name == warning.targetName } ?: return@launch
            val ok = repo.rename(item, newName, true)
            if (ok) snack(R.string.snack_rename_success, newName) else snack(R.string.snack_rename_failed)
            dismissAndRefresh()
        } else {
            // 普通重命名对话框
            val renameDialog = state.dialog as? BrowserDialog.Rename ?: return@launch
            val ok = repo.rename(renameDialog.item, newName, state.isShizukuMode)
            if (ok) snack(R.string.snack_rename_success, newName) else snack(R.string.snack_rename_failed)
            dismissAndRefresh()
        }
    }

    fun showDetailsDialog(item: FileItem) {
        _uiState.update { it.copy(dialog = BrowserDialog.Details(item)) }
    }

    fun deleteSingle(item: FileItem) = viewModelScope.launch {
        val state = _uiState.value
        if (state.isShizukuMode && repo.isSensitivePath(item.path)) {
            _uiState.update { it.copy(dialog = BrowserDialog.ShizukuWarning(
                action = ShizukuAction.DELETE,
                targetName = item.name,
                detail = item.path,
            )) }
        } else {
            val ok = repo.deleteSingle(item, state.isShizukuMode)
            snack(R.string.snack_deleted, if (ok) 1 else 0)
            dismissAndRefresh()
        }
    }

    fun confirmShizukuAction(action: ShizukuAction, targetName: String, detail: String) = viewModelScope.launch {
        _uiState.update { it.copy(dialog = BrowserDialog.None) }
        when (action) {
            ShizukuAction.DELETE -> {
                val state = _uiState.value
                val item = state.items.firstOrNull { it.name == targetName } ?: return@launch
                val ok = repo.deleteSingle(item, true)
                snack(R.string.snack_deleted, if (ok) 1 else 0)
                dismissAndRefresh()
            }
            ShizukuAction.DELETE_MULTI -> {
                val state = _uiState.value
                val items = state.items.filter { it.uri in state.selectedUris }
                val n = repo.delete(items, true)
                snack(R.string.snack_deleted, n)
                exitSelection()
                refresh(state.currentRoot ?: return@launch, state.currentPath.ifBlank { null })
            }
            ShizukuAction.RENAME -> {
                val state = _uiState.value
                val item = state.items.firstOrNull { it.name == targetName } ?: return@launch
                _uiState.update { it.copy(dialog = BrowserDialog.Rename(item)) }
            }
            ShizukuAction.CREATE_FOLDER -> {
                val state = _uiState.value
                _uiState.update { it.copy(dialog = BrowserDialog.NewFolder) }
            }
            ShizukuAction.CREATE_FILE -> {
                val state = _uiState.value
                _uiState.update { it.copy(dialog = BrowserDialog.NewFile) }
            }
            ShizukuAction.INSTALL -> {
                val ok = shizukuManager.installApk(detail)
                if (ok) snack(R.string.snack_apk_installed) else snack(R.string.snack_apk_install_failed)
            }
            ShizukuAction.UNINSTALL -> {
                val ok = shizukuManager.uninstallApp(detail)
                if (ok) snack(R.string.snack_app_uninstalled, targetName) else snack(R.string.snack_app_uninstall_failed)
            }
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = BrowserDialog.None) }
    }

    fun openFile(item: FileItem) {
        val state = _uiState.value
        // Shizuku 模式下 APK 文件直接静默安装
        if (state.isShizukuMode && item.category == FileCategory.APP) {
            confirmInstallApk(item.path)
            return
        }
        val event = when (item.category) {
            FileCategory.IMAGE -> BrowserEvent.OpenImage(item.path, item.name)
            FileCategory.VIDEO -> BrowserEvent.OpenVideo(item.path, item.name)
            FileCategory.AUDIO -> BrowserEvent.OpenAudio(item.path, item.name)
            FileCategory.TEXT, FileCategory.CODE -> BrowserEvent.OpenText(item.path, item.name)
            FileCategory.ARCHIVE -> BrowserEvent.OpenArchive(item.path, item.name)
            FileCategory.PDF -> BrowserEvent.OpenPdf(item.path, item.name)
            else -> { openExternal(item); return }
        }
        viewModelScope.launch { _events.send(event) }
    }

    fun confirmInstallApk(apkPath: String) {
        val state = _uiState.value
        _uiState.update { it.copy(dialog = BrowserDialog.ShizukuWarning(
            action = ShizukuAction.INSTALL,
            targetName = apkPath.substringAfterLast('/'),
            detail = apkPath,
        )) }
    }

    fun confirmUninstall(packageName: String) {
        _uiState.update { it.copy(dialog = BrowserDialog.ShizukuWarning(
            action = ShizukuAction.UNINSTALL,
            targetName = packageName,
            detail = packageName,
        )) }
    }

    fun openExternal(item: FileItem) {
        val ctx = getApplication<Application>()
        val viewIntent = FileUtils.buildOpenIntent(ctx, item)
        if (viewIntent == null) {
            snack(R.string.snack_open_failed)
            return
        }
        if (viewIntent.resolveActivity(ctx.packageManager) == null) {
            snack(R.string.snack_no_app_to_open)
            return
        }
        val chooser = Intent.createChooser(viewIntent, ctx.getString(R.string.chooser_open_with))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    // ---- 书签 ----

    /** 将当前目录加入书签（仅文件路径模式） */
    fun addCurrentBookmark() = viewModelScope.launch {
        val state = _uiState.value
        if (state.isSafMode || state.currentPath.isBlank()) {
            snack(R.string.snack_bookmark_unavailable)
            return@launch
        }
        val root = state.currentRoot
        val name = if (state.currentPath == root?.file?.absolutePath) {
            root.displayName
        } else {
            state.currentPath.substringAfterLast('/').ifBlank { state.currentPath }
        }
        bookmarkStore.addFileBookmark(name, state.currentPath)
        snack(R.string.snack_bookmark_added, name)
    }

    /** 请求移除当前目录书签：弹出确认对话框 */
    fun requestRemoveCurrentBookmark() {
        val state = _uiState.value
        val bookmark = state.bookmarks.firstOrNull {
            !it.isSaf && it.path == state.currentPath
        } ?: return
        _uiState.update {
            it.copy(dialog = BrowserDialog.RemoveBookmark(bookmark.name, bookmark.id))
        }
    }

    /** 确认移除书签；SAF 书签同时释放权限 */
    fun confirmRemoveBookmark(id: String) = viewModelScope.launch {
        val bookmark = _uiState.value.bookmarks.firstOrNull { it.id == id } ?: return@launch
        if (bookmark.isSaf) {
            runCatching { safManager.releasePermission(Uri.parse(bookmark.treeUri)) }
        }
        bookmarkStore.remove(id)
        _uiState.update { it.copy(dialog = BrowserDialog.None) }
        snack(R.string.snack_bookmark_removed, bookmark.name)
    }

    /** 点击书签导航到对应目录 */
    fun selectBookmark(bookmark: Bookmark) = viewModelScope.launch {
        if (bookmark.isSaf) {
            // 校验权限仍有效
            val treeUri = Uri.parse(bookmark.treeUri)
            if (!safManager.isAuthorized(treeUri)) {
                snack(R.string.snack_saf_permission_lost, safProviderName(bookmark.treeUri))
                return@launch
            }
            val root = StorageRoot(
                displayName = bookmark.name,
                uri = treeUri,
                file = null,
                isSaf = true,
            )
            refresh(root, bookmark.treeUri)
        } else {
            val dir = File(bookmark.path)
            if (!dir.exists() || !dir.isDirectory) {
                snack(R.string.snack_bookmark_invalid)
                return@launch
            }
            // 使用第一个文件根作为宿主（仅用于刷新逻辑）
            val host = _uiState.value.roots.firstOrNull()
                ?: StorageRoot("内部存储", Uri.fromFile(dir.parentFile ?: dir), dir.parentFile ?: dir, false)
            refresh(host, bookmark.path)
        }
    }

    /**
     * SAF 授权回调：取得持久化权限 + 加入书签 + 导航到该目录。
     * 在 Activity 的 SAF 结果回调中调用。
     */
    fun onSafGranted(treeUri: Uri) = viewModelScope.launch {
        safManager.takePermission(treeUri)
        val name = safManager.displayName(treeUri) ?: treeUri.lastPathSegment ?: "SAF 目录"
        bookmarkStore.addSafBookmark(name, treeUri.toString())
        snack(R.string.snack_saf_added, name)
        val root = StorageRoot(name, treeUri, null, isSaf = true)
        refresh(root, treeUri.toString())
    }

    private fun refresh(root: StorageRoot, path: String?) = viewModelScope.launch {
        if (root.isSaf) {
            refreshSaf(root, path)
        } else {
            refreshFile(root, path)
        }
    }

    private suspend fun refreshFile(root: StorageRoot, path: String?) {
        val dir = path?.let { File(it) } ?: root.file ?: return
        val isShizuku = root.isShizuku
        if (!dir.exists() || !dir.isDirectory) {
            _uiState.update {
                it.copy(
                    loadState = BrowserLoadState.Error,
                    errorMessage = getApplication<Application>().getString(R.string.snack_error_open_folder),
                )
            }
            return
        }
        _uiState.update { it.copy(loadState = BrowserLoadState.Loading, currentRoot = root) }

        val items = repo.listDirectory(dir, _uiState.value.sortConfig, _uiState.value.showHidden, isShizuku)
        val crumbs = repo.breadcrumbs(root.file ?: dir, dir)

        val currentPath = dir.absolutePath
        val isBookmarked = _uiState.value.bookmarks.any { !it.isSaf && it.path == currentPath }
        val bookmarkName = _uiState.value.bookmarks.firstOrNull { !it.isSaf && it.path == currentPath }?.name ?: ""

        _uiState.update {
            it.copy(
                loadState = BrowserLoadState.Ready,
                currentRoot = root,
                currentPath = currentPath,
                isSafMode = false,
                isShizukuMode = isShizuku,
                breadcrumbs = crumbs,
                items = items,
                itemCount = items.size,
                errorMessage = null,
                currentPathIsBookmarked = isBookmarked,
                currentBookmarkName = bookmarkName,
            )
        }
        applySearchFilter()
    }

    private suspend fun refreshSaf(root: StorageRoot, path: String?) {
        val uri = path ?: root.uri.toString()
        _uiState.update { it.copy(loadState = BrowserLoadState.Loading, currentRoot = root) }
        val accessible = withContext(kotlinx.coroutines.Dispatchers.IO) { safManager.isDirectory(uri) }
        if (!accessible) {
            _uiState.update {
                it.copy(
                    loadState = BrowserLoadState.Error,
                    errorMessage = getApplication<Application>().getString(
                        R.string.snack_saf_app_not_started,
                        safProviderName(uri),
                    ),
                    currentRoot = root,
                )
            }
            return
        }
        val items = withContext(kotlinx.coroutines.Dispatchers.IO) {
            safManager.listDirectory(uri, _uiState.value.sortConfig, _uiState.value.showHidden)
        }
        val crumbs = withContext(kotlinx.coroutines.Dispatchers.IO) {
            safManager.breadcrumbs(root.uri.toString(), uri, root.displayName)
        }
        _uiState.update {
            it.copy(
                loadState = BrowserLoadState.Ready,
                currentRoot = root,
                currentPath = uri,
                isSafMode = true,
                breadcrumbs = crumbs,
                items = items,
                itemCount = items.size,
                errorMessage = null,
                currentPathIsBookmarked = false,
                currentBookmarkName = "",
            )
        }
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val state = _uiState.value
        val q = state.searchQuery.trim()
        val filtered = if (q.isEmpty()) state.items
        else state.items.filter { it.name.contains(q, ignoreCase = true) }
        _uiState.update { it.copy(filteredItems = filtered) }
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }
}
