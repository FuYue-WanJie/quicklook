@file:OptIn(ExperimentalFoundationApi::class)

package wanjie.quicklook.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import wanjie.quicklook.R
import wanjie.quicklook.data.ArchiveEntry
import wanjie.quicklook.data.FileCategory
import wanjie.quicklook.data.FileItem
import wanjie.quicklook.data.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    path: String,
    displayName: String,
    onBack: () -> Unit,
    onOpenImage: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenVideo: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenAudio: (path: String, name: String) -> Unit = { _, _ -> },
    onOpenText: (path: String, name: String) -> Unit = { _, _ -> },
) {
    val viewModel: ArchiveViewerViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(path) {
        viewModel.init(path, displayName)
    }

    // 提取完成后，根据文件类型导航到对应查看器
    LaunchedEffect(state.pendingOpen) {
        val pending = state.pendingOpen ?: return@LaunchedEffect
        viewModel.consumePendingOpen()
        when (pending.category) {
            FileCategory.IMAGE -> onOpenImage(pending.path, pending.name)
            FileCategory.VIDEO -> onOpenVideo(pending.path, pending.name)
            FileCategory.AUDIO -> onOpenAudio(pending.path, pending.name)
            FileCategory.TEXT, FileCategory.CODE -> onOpenText(pending.path, pending.name)
            else -> {
                // 其他类型使用系统应用打开
                val intent = FileUtils.buildOpenIntent(
                    context,
                    FileItem(
                        uri = android.net.Uri.EMPTY,
                        name = pending.name,
                        path = pending.path,
                        isDirectory = false,
                        size = 0L,
                        lastModified = 0L,
                        mimeType = FileUtils.mimeTypeFor(pending.name),
                        category = pending.category,
                    ),
                )
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    // 显示提取错误
    LaunchedEffect(state.extractError) {
        val err = state.extractError ?: return@LaunchedEffect
        snackbarHost.showSnackbar(err)
        viewModel.clearExtractError()
    }

    BackHandler {
        if (!viewModel.goUp()) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.totalEntries > 0) {
                            Text(
                                text = stringResource(R.string.archive_item_count, state.totalEntries),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.goUp()) onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.archive_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Crossfade(
                targetState = state.loadState,
                animationSpec = tween(300),
                label = "archive_state",
            ) { loadState ->
                when (loadState) {
                    ArchiveLoadState.Loading -> LoadingView()
                    ArchiveLoadState.NeedPassword -> PasswordDialog(
                        onSubmit = { viewModel.submitPassword(it) },
                    )
                    ArchiveLoadState.Error -> ErrorView(state.errorMessage ?: stringResource(R.string.archive_read_failed, ""))
                    ArchiveLoadState.Ready -> ArchiveContent(
                        state = state,
                        onCrumbClick = { viewModel.navigateTo(it) },
                        onEntryClick = { entry ->
                            if (entry.isDirectory) {
                                viewModel.navigateTo(entry.path)
                            } else if (!entry.encrypted) {
                                viewModel.openEntry(entry)
                            }
                        },
                    )
                }
            }
            // 提取进度遮罩
            AnimatedVisibility(
                visible = state.extracting,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ArchiveContent(
    state: ArchiveUiState,
    onCrumbClick: (String) -> Unit,
    onEntryClick: (ArchiveEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 虚拟路径面包屑
        if (state.crumbs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.crumbs.forEachIndexed { index, crumb ->
                    if (index > 0) {
                        Text(
                            "/",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { onCrumbClick(crumb.innerPath) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            crumb.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (index == state.crumbs.lastIndex)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (state.entries.isEmpty()) {
            EmptyArchive()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.entries, key = { it.path }) { entry ->
                    ArchiveEntryItem(
                        entry,
                        onClick = { onEntryClick(entry) },
                        modifier = Modifier.animateItemPlacement(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryItem(
    entry: ArchiveEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Rounded.Folder
                else if (entry.encrypted) Icons.Rounded.Lock
                else Icons.Rounded.InsertDriveFile,
            contentDescription = null,
            tint = when {
                entry.isDirectory -> MaterialTheme.colorScheme.primary
                entry.encrypted -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (entry.encrypted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                entry.isDirectory -> stringResource(R.string.archive_folder)
                entry.encrypted -> stringResource(R.string.archive_entry_encrypted)
                else -> buildString {
                    append(FileUtils.formatSize(entry.size))
                    if (entry.compressionRatio > 0) append("  ·  ").append(stringResource(R.string.archive_compressed, entry.compressionRatio))
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.archive_parsing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyArchive() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.archive_empty),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PasswordDialog(onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.archive_password_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text(stringResource(R.string.archive_password_label)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (password.isNotEmpty()) onSubmit(password) },
                enabled = password.isNotEmpty(),
            ) { Text(stringResource(R.string.archive_confirm)) }
        },
    )
}
