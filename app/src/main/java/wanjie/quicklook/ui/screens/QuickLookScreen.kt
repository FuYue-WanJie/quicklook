package wanjie.quicklook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wanjie.quicklook.data.AndroidDataAccess
import wanjie.quicklook.data.FileEntry
import wanjie.quicklook.ui.components.AndroidDataPromptDialog
import wanjie.quicklook.ui.components.FileRow
import wanjie.quicklook.ui.components.FileRowDivider
import wanjie.quicklook.viewmodel.FileViewModel
import wanjie.quicklook.viewmodel.NavigateResult
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLookScreen(
    viewModel: FileViewModel,
    onOpen: (File) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 路径跳转对话框状态
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }

    // Android/data 下文件/文件夹操作的安全警告待确认项
    var securityWarningEntry by remember { mutableStateOf<FileEntry?>(null) }

    /** 真正执行打开：目录则进入，文件则交给路由。 */
    fun openEntry(entry: FileEntry) {
        val file = File(entry.path)
        if (entry.isDirectory) viewModel.open(file) else onOpen(file)
    }

    fun doJump() {
        val result = viewModel.navigateTo(jumpInput)
        showJumpDialog = false
        val msg = when (result) {
            is NavigateResult.Success -> return // 成功则无提示，直接刷新列表
            is NavigateResult.Empty -> "路径不能为空"
            is NavigateResult.NotFound -> "路径不存在：${result.path}"
            is NavigateResult.NotDirectory -> "不是目录：${result.path}"
            is NavigateResult.NoPermission -> "无权限访问：${result.path}"
        }
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "快览",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PathBreadcrumb(
                            path = state.currentPath,
                            onSegmentClick = { viewModel.open(it) },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "打开侧栏")
                    }
                },
                actions = {
                    // 路径跳转：用户输入目录路径直达
                    IconButton(onClick = {
                        jumpInput = state.currentPath
                        showJumpDialog = true
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "跳转路径")
                    }
                    if (state.canGoUp) {
                        IconButton(onClick = { viewModel.goUp() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回上级")
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> EmptyState(text = state.error!!)
                state.entries.isEmpty() -> EmptyState(text = "空文件夹")
                else -> FileList(
                    entries = state.entries,
                    onItemClick = { entry ->
                        // 操作 Android/data 下的文件/文件夹：先弹安全警告二次确认
                        if (AndroidDataAccess.isStrictlyUnderAndroidData(entry.path)) {
                            securityWarningEntry = entry
                        } else {
                            openEntry(entry)
                        }
                    },
                )
            }
        }
    }

    // 路径跳转对话框
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("跳转到目录") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it },
                    label = { Text("目录路径") },
                    placeholder = { Text("/storage/emulated/0/Download") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { doJump() }) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("取消") }
            },
        )
    }

    // Android/data 无权限绕过 / 绕过失败弹窗
    state.androidDataPrompt?.let { prompt ->
        AndroidDataPromptDialog(
            prompt = prompt,
            onConfirmBypass = { viewModel.confirmBypass() },
            onDismiss = { viewModel.dismissAndroidDataPrompt() },
        )
    }

    // 操作 Android/data 下文件/文件夹的安全警告二次确认
    securityWarningEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { securityWarningEntry = null },
            title = { Text("安全警告") },
            text = {
                Text(
                    "即将${if (entry.isDirectory) "进入" else "打开"} Android/data 下的" +
                        "${if (entry.isDirectory) "文件夹" else "文件"}「${entry.name}」。\n" +
                        "该目录包含应用私有数据，误操作可能影响对应应用的运行。是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    securityWarningEntry = null
                    openEntry(entry)
                }) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { securityWarningEntry = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FileList(
    entries: List<FileEntry>,
    onItemClick: (FileEntry) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // contentType 让目录与文件两类行分别复用，减少大列表重组开销
        items(
            items = entries,
            key = { it.path },
            contentType = { if (it.isDirectory) "dir" else "file" },
        ) { entry ->
            FileRow(entry = entry, onClick = { onItemClick(entry) })
            FileRowDivider()
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(8.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PathBreadcrumb(
    path: String,
    onSegmentClick: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (path.isEmpty()) {
        Text(
            text = "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val segments = remember(path) {
        buildList {
            var current = File(path)
            while (true) {
                add(0, current)
                current = current.parentFile ?: break
            }
        }
    }
    val scrollState = rememberScrollState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .horizontalScroll(scrollState)
            .fillMaxWidth(),
    ) {
        segments.forEachIndexed { index, file ->
            Text(
                text = file.name.ifEmpty { "/" },
                style = MaterialTheme.typography.labelMedium,
                color = if (index == segments.lastIndex)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(
                        enabled = index != segments.lastIndex,
                        onClick = { onSegmentClick(file) },
                    )
                    .padding(horizontal = 2.dp),
            )
            if (index < segments.lastIndex) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}
