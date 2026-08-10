@file:OptIn(ExperimentalFoundationApi::class)

package wanjie.quicklook.ui.browser

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.drawable.toBitmap
import wanjie.quicklook.R
import wanjie.quicklook.data.ApkInfo
import wanjie.quicklook.data.FileCategory
import wanjie.quicklook.data.FileItem
import wanjie.quicklook.data.FileUtils
import wanjie.quicklook.ui.components.Breadcrumbs
import wanjie.quicklook.ui.components.EmptyState
import wanjie.quicklook.ui.components.FileListItem
import wanjie.quicklook.ui.components.SortSheet
import wanjie.quicklook.ui.components.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {},
    onOpenImage: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenVideo: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenAudio: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenText: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenArchive: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenPdf: (path: String, name: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is BrowserEvent.ShowSnackbar -> snackbarHost.showSnackbar(ev.message)
                is BrowserEvent.OpenImage -> onOpenImage(ev.path, ev.name)
                is BrowserEvent.OpenVideo -> onOpenVideo(ev.path, ev.name)
                is BrowserEvent.OpenAudio -> onOpenAudio(ev.path, ev.name)
                is BrowserEvent.OpenText -> onOpenText(ev.path, ev.name)
                is BrowserEvent.OpenArchive -> onOpenArchive(ev.path, ev.name)
                is BrowserEvent.OpenPdf -> onOpenPdf(ev.path, ev.name)
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> viewModel.onPermissionResult(result.values.any { it }) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        viewModel.onPermissionResult(granted)
    }

    // SAF 目录授权：选择一个外部目录并取得持久化权限
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onSafGranted(uri)
    }

    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            manageStorageLauncher.launch(intent)
        } else {
            permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    // 返回键优先级：选择模式 > 搜索模式 > 回退上级目录 > 退出 App
    val canGoUp = state.breadcrumbs.size > 1
    BackHandler(enabled = state.selectionMode || state.searchMode || canGoUp) {
        when {
            state.selectionMode -> viewModel.exitSelection()
            state.searchMode -> viewModel.exitSearchMode()
            canGoUp -> viewModel.goUp()
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = {
            when {
                state.selectionMode -> ContextualActionBar(
                    selectedCount = state.selectedCount,
                    onClose = { viewModel.exitSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onInvert = { viewModel.invertSelection() },
                    onDelete = { viewModel.deleteSelected() },
                )
                state.searchMode -> SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClose = { viewModel.exitSearchMode() },
                )
                else -> TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.currentRoot?.displayName ?: stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(R.string.item_count, state.itemCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.drawer_open))
                        }
                    },
                    actions = {
                        OverflowActionsMenu(
                            state = state,
                            onSearch = { viewModel.enterSearchMode() },
                            onSort = { viewModel.toggleSortSheet() },
                            onJump = { viewModel.showJumpDialog() },
                            onAddSaf = { safLauncher.launch(null) },
                            onAddBookmark = { viewModel.addCurrentBookmark() },
                            onRemoveBookmark = { viewModel.requestRemoveCurrentBookmark() },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        floatingActionButton = {
            // 仅在 Ready 且非选择/搜索模式时显示 FAB
            if (state.loadState == BrowserLoadState.Ready && !state.selectionMode && !state.searchMode) {
                ExpandableFab(
                    onNewFolder = { viewModel.showNewFolderDialog() },
                    onNewFile = { viewModel.showNewFileDialog() },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        if (state.searchMode) {
            SearchModeContent(
                state = state,
                innerPadding = innerPadding,
                onScopeChange = { viewModel.setSearchScope(it) },
                onToggleRegex = { viewModel.setUseRegex(!state.useRegex) },
                onItemClick = { item ->
                    if (item.isDirectory) {
                        viewModel.navigateToPath(item.path)
                    } else {
                        viewModel.openFile(item)
                    }
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (state.breadcrumbs.isNotEmpty() && !state.selectionMode) {
                    Breadcrumbs(
                        segments = state.breadcrumbs,
                        onSegmentClick = { seg -> viewModel.navigateToBreadcrumb(seg.uri) },
                    )
                }

                when (state.loadState) {
                    BrowserLoadState.Loading -> LoadingState()
                    BrowserLoadState.Error -> EmptyState(
                        title = stringResource(R.string.empty_title),
                        subtitle = state.errorMessage ?: stringResource(R.string.empty_subtitle),
                    )
                    BrowserLoadState.NeedsPermission -> PermissionState(onGrantClick = { requestPermission() })
                    BrowserLoadState.Ready -> {
                        val items = state.filteredItems
                        if (items.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.empty_title),
                                subtitle = stringResource(R.string.empty_subtitle),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(items, key = { it.uri.toString() + it.name }) { item ->
                                    FileListItem(
                                        item = item,
                                        isSelected = item.uri in state.selectedUris,
                                        selectionMode = state.selectionMode,
                                        onClick = { viewModel.onItemClicked(item) },
                                        onLongClick = { viewModel.onItemLongClicked(item) },
                                        onDetails = { viewModel.showDetailsDialog(item) },
                                        onRename = { viewModel.showRenameDialog(item) },
                                        onDelete = { viewModel.deleteSingle(item) },
                                        onShare = { /* 后续实现 */ },
                                        onOpenWith = { viewModel.openExternal(item) },
                                        modifier = Modifier.animateItemPlacement(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 排序底部弹窗
        if (state.showSortSheet) {
            SortSheet(
                current = state.sortConfig,
                onConfigChange = { viewModel.setSortConfig(it) },
                onDismiss = { viewModel.toggleSortSheet() },
            )
        }

        // 对话框
        when (val dialog = state.dialog) {
            is BrowserDialog.NewFolder -> NameInputDialog(
                title = stringResource(R.string.dialog_new_folder_title),
                hint = stringResource(R.string.dialog_new_folder_hint),
                confirmText = stringResource(R.string.dialog_create),
                onConfirm = { name -> viewModel.confirmCreateFolder(name) },
                onDismiss = { viewModel.dismissDialog() },
            )
            is BrowserDialog.NewFile -> NameInputDialog(
                title = stringResource(R.string.dialog_new_file_title),
                hint = stringResource(R.string.dialog_new_file_hint),
                confirmText = stringResource(R.string.dialog_create),
                onConfirm = { name -> viewModel.confirmCreateFile(name) },
                onDismiss = { viewModel.dismissDialog() },
            )
            is BrowserDialog.Rename -> NameInputDialog(
                title = stringResource(R.string.dialog_rename_title),
                hint = stringResource(R.string.dialog_rename_hint),
                initial = dialog.item.name,
                confirmText = stringResource(R.string.dialog_rename),
                onConfirm = { name -> viewModel.confirmRename(name) },
                onDismiss = { viewModel.dismissDialog() },
            )
            is BrowserDialog.ShizukuWarning -> ShizukuWarningDialog(
                action = dialog.action,
                targetName = dialog.targetName,
                detail = dialog.detail,
                onConfirm = { viewModel.confirmShizukuAction(dialog.action, dialog.targetName, dialog.detail) },
                onDismiss = { viewModel.dismissDialog() },
            )
            is BrowserDialog.Details -> DetailsDialog(
                item = dialog.item,
                onDismiss = { viewModel.dismissDialog() },
            )
            is BrowserDialog.JumpToPath -> NameInputDialog(
                title = stringResource(R.string.dialog_jump_title),
                hint = stringResource(R.string.dialog_jump_hint),
                initial = dialog.initial,
                confirmText = stringResource(R.string.dialog_jump),
                onConfirm = { path -> viewModel.jumpToPath(path) },
                onDismiss = { viewModel.dismissDialog() },
            )
            is BrowserDialog.RemoveBookmark -> AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.dialog_remove_bookmark_title)) },
                text = { Text(stringResource(R.string.dialog_remove_bookmark_msg, dialog.name)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmRemoveBookmark(dialog.id) }) {
                        Text(stringResource(R.string.dialog_remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
            BrowserDialog.None -> { /* 无 */ }
        }
    }
}

/** 顶栏右上溢出菜单：搜索 / 排序 / 跳转目录 / 添加 SAF / 添加|移除书签 */
@Composable
private fun OverflowActionsMenu(
    state: BrowserUiState,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onJump: () -> Unit,
    onAddSaf: () -> Unit,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // 搜索
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                onClick = { expanded = false; onSearch() },
            )
            // 排序
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_by)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
                onClick = { expanded = false; onSort() },
            )
            // 跳转目录（仅文件路径模式）
            if (!state.isSafMode) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_jump_path)) },
                    leadingIcon = { Icon(Icons.Rounded.GpsFixed, null) },
                    onClick = { expanded = false; onJump() },
                )
            }
            // 添加 SAF 目录
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_saf)) },
                leadingIcon = { Icon(Icons.Rounded.Storage, null) },
                onClick = { expanded = false; onAddSaf() },
            )
            // 添加 / 移除书签（文件路径模式和 Shizuku 模式）
            if (!state.isSafMode && state.currentPath.isNotBlank()) {
                if (state.currentPathIsBookmarked) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove_bookmark)) },
                        leadingIcon = { Icon(Icons.Rounded.BookmarkRemove, null) },
                        onClick = { expanded = false; onRemoveBookmark() },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_bookmark)) },
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, null) },
                        onClick = { expanded = false; onAddBookmark() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualActionBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.selected_count, selectedCount),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
            }
            IconButton(onClick = onInvert) {
                Icon(Icons.Rounded.Deselect, contentDescription = stringResource(R.string.action_invert))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
            }
            IconButton(onClick = { /* 分享 */ }) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    hint: String,
    initial: String = "",
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(hint) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DetailsDialog(item: FileItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // APK 异步解析：应用图标 + 基本信息
    var apkInfo by remember { mutableStateOf<ApkInfo?>(null) }
    var apkIcon by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var apkLoading by remember { mutableStateOf(item.category == FileCategory.APP) }
    LaunchedEffect(item.path) {
        if (item.category == FileCategory.APP) {
            val info = withContext(Dispatchers.IO) { FileUtils.parseApk(context, item.path) }
            apkInfo = info
            apkIcon = withContext(Dispatchers.IO) {
                info?.icon?.toBitmap(192, 192)?.asImageBitmap()
            }
            apkLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // APK 图标 + 应用名
                if (item.category == FileCategory.APP) {
                    val icon = apkIcon
                    val label = apkInfo?.label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                        if (label != null) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                DetailRow(stringResource(R.string.detail_name), item.name)
                DetailRow(stringResource(R.string.detail_path), item.path)
                DetailRow(stringResource(R.string.detail_size), FileUtils.formatSize(if (item.isDirectory) 0 else item.size))
                DetailRow(stringResource(R.string.detail_modified), FileUtils.formatDate(item.lastModified))
                DetailRow(stringResource(R.string.detail_type), item.mimeType)
                // APK 专属字段
                if (item.category == FileCategory.APP) {
                    if (apkLoading) {
                        Text(
                            text = stringResource(R.string.apk_parsing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    apkInfo?.let { info ->
                        info.packageName?.let { DetailRow(stringResource(R.string.apk_package), it) }
                        val ver = buildString {
                            info.versionName?.let { append(it) }
                            append(" (").append(info.versionCode).append(")")
                        }
                        DetailRow(stringResource(R.string.apk_version), ver)
                        DetailRow(stringResource(R.string.apk_min_sdk), FileUtils.sdkVersionLabel(info.minSdk))
                        DetailRow(stringResource(R.string.apk_target_sdk), FileUtils.sdkVersionLabel(info.targetSdk))
                        DetailRow(stringResource(R.string.apk_permissions), "${info.permissions.size}")
                        val signature = info.signingSha1
                        DetailRow(
                            stringResource(R.string.apk_signature),
                            if (signature != null) {
                                signature.chunked(4).joinToString(" ")
                            } else {
                                stringResource(R.string.apk_unsigned)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LoadingState() = CenteredLoading(stringResource(R.string.loading))

/** 居中的圆形加载指示器 + 说明文本，浏览器与搜索模式共用。 */
@Composable
private fun CenteredLoading(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionState(onGrantClick: () -> Unit) {
    val isR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(R.string.permission_required),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(if (isR) R.string.permission_manage_desc else R.string.permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrantClick) {
                Text(text = stringResource(if (isR) R.string.open_settings else R.string.grant_access))
            }
        }
    }
}

/**
 * 展开式 FAB：点击主 FAB 朝上展开两个子选项（新建文件夹 / 新建文件）。
 */
@Composable
private fun ExpandableFab(
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 展开时显示两个子项
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FabItem(
                    label = stringResource(R.string.fab_new_file),
                    icon = Icons.Rounded.InsertDriveFile,
                    onClick = { expanded = false; onNewFile() },
                )
                FabItem(
                    label = stringResource(R.string.fab_new_folder),
                    icon = Icons.Rounded.CreateNewFolder,
                    onClick = { expanded = false; onNewFolder() },
                )
            }
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = if (expanded) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (expanded) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Add,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun FabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}

// ---- 搜索模式 UI ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SearchModeContent(
    state: BrowserUiState,
    innerPadding: PaddingValues,
    onScopeChange: (SearchScope) -> Unit,
    onToggleRegex: () -> Unit,
    onItemClick: (FileItem) -> Unit,
) {
    val resultListState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        // 范围切换 + 正则开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow {
                val scopes = listOf(
                    SearchScope.ALL to stringResource(R.string.search_scope_all),
                    SearchScope.FILES_ONLY to stringResource(R.string.search_scope_files),
                    SearchScope.FOLDERS_ONLY to stringResource(R.string.search_scope_folders),
                )
                scopes.forEachIndexed { index, (scope, label) ->
                    SegmentedButton(
                        selected = state.searchScope == scope,
                        onClick = { onScopeChange(scope) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.search_use_regex),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = state.useRegex,
                onCheckedChange = { onToggleRegex() },
            )
        }

        Divider()

        if (state.searching) {
            CenteredLoading(stringResource(R.string.search_searching))
        } else if (state.searchQuery.isBlank()) {
            EmptyState(
                title = stringResource(R.string.search),
                subtitle = stringResource(R.string.search_empty),
            )
        } else if (state.searchResults.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_title),
                subtitle = stringResource(R.string.search_no_result),
            )
        } else {
            LazyColumn(
                state = resultListState,
                modifier = Modifier.fillMaxSize().verticalScrollbar(resultListState),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.searchResults, key = { it.path }) { item ->
                    SearchResultItem(
                        item = item,
                        rootPath = state.currentRoot?.file?.absolutePath.orEmpty(),
                        onClick = { onItemClick(item) },
                        modifier = Modifier.animateItemPlacement(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: FileItem,
    rootPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        wanjie.quicklook.ui.components.FileIcon(
            category = item.category,
            name = item.name,
            size = 40,
            path = item.path,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 相对于根目录的路径
            val relPath = if (rootPath.isNotBlank() && item.path.startsWith(rootPath)) {
                item.path.removePrefix(rootPath).trimStart('/').let { if (it.isEmpty()) "/" else it }
            } else {
                item.path
            }
            Text(
                text = relPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shizuku 安全操作警告对话框 */
@Composable
private fun ShizukuWarningDialog(
    action: ShizukuAction,
    targetName: String,
    detail: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actionText = when (action) {
        ShizukuAction.DELETE, ShizukuAction.DELETE_MULTI -> stringResource(R.string.shizuku_warning_delete)
        ShizukuAction.RENAME -> stringResource(R.string.shizuku_warning_rename)
        ShizukuAction.CREATE_FOLDER, ShizukuAction.CREATE_FILE -> stringResource(R.string.shizuku_warning_create)
        ShizukuAction.INSTALL -> stringResource(R.string.shizuku_warning_install)
        ShizukuAction.UNINSTALL -> stringResource(R.string.shizuku_warning_uninstall)
    }
    val warningMsg = if (targetName.isNotBlank()) {
        stringResource(R.string.shizuku_warning_message, actionText, targetName, if (detail.isNotBlank()) "\n路径: $detail" else "")
    } else {
        stringResource(R.string.shizuku_warning_message, actionText, detail, "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.shizuku_warning_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = warningMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (action == ShizukuAction.DELETE_MULTI) {
                    Text(
                        text = stringResource(R.string.shizuku_warning_multiple),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (action == ShizukuAction.UNINSTALL) {
                    Text(
                        text = stringResource(R.string.shizuku_warning_uninstall_msg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Text(stringResource(R.string.shizuku_warning_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.shizuku_warning_cancel))
            }
        },
    )
}
