package wanjie.quicklook.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wanjie.quicklook.data.FileRepository
import wanjie.quicklook.ui.screens.ImageViewerScreen
import wanjie.quicklook.ui.screens.QuickLookScreen
import wanjie.quicklook.ui.screens.SettingsScreen
import wanjie.quicklook.ui.screens.TextEditorScreen
import wanjie.quicklook.viewmodel.FileViewModel
import wanjie.quicklook.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

private enum class AppScreen { FILES, SETTINGS, TEXT_EDITOR, IMAGE_VIEWER }

private data class QuickLocation(val label: String, val icon: ImageVector, val path: () -> File?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    fileViewModel: FileViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenWithThirdParty: (File) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(AppScreen.FILES) }
    var openedFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val openedFile: File? get() = openedFilePath?.let(::File)

    /**
     * 文件点击路由：文本类用内置编辑器，图片类用内置查看器，其余交给第三方应用。
     */
    fun onFileClick(file: File) {
        when {
            file.isDirectory -> fileViewModel.open(file)
            FileRepository.isEditableText(file) -> {
                openedFilePath = file.absolutePath
                screen = AppScreen.TEXT_EDITOR
            }
            FileRepository.isViewableImage(file) -> {
                openedFilePath = file.absolutePath
                screen = AppScreen.IMAGE_VIEWER
            }
            else -> onOpenWithThirdParty(file)
        }
    }

    // 设置变化时刷新文件列表（例如切换“显示隐藏文件夹”后立即生效）
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(settings.showHidden) {
        fileViewModel.refresh()
    }

    val quickLocations = remember {
        listOf(
            QuickLocation("内部存储", Icons.Outlined.Storage) { FileRepository.defaultRoot() },
            QuickLocation("下载", Icons.Outlined.Download) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            },
            QuickLocation("文档", Icons.Outlined.Description) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            },
            QuickLocation("音乐", Icons.Outlined.MusicNote) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            },
            QuickLocation("视频", Icons.Outlined.Movie) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            },
            QuickLocation("图片", Icons.Outlined.Image) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            },
            QuickLocation("相机", Icons.Outlined.PhotoCamera) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // 侧栏不再放置顶部 header（按需求移除“顶部那个东西”）
                Column(
                    Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(vertical = 6.dp),
                ) {
                    quickLocations.forEach { loc ->
                        val target = loc.path()
                        if (target != null && target.exists()) {
                            CompactDrawerItem(
                                label = loc.label,
                                icon = loc.icon,
                                onClick = {
                                    fileViewModel.open(target)
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider()
                    // 设置固定在侧栏最底部
                    CompactDrawerItem(
                        label = "设置",
                        icon = Icons.Outlined.Settings,
                        onClick = {
                            screen = AppScreen.SETTINGS
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        when (screen) {
            AppScreen.FILES -> QuickLookScreen(
                viewModel = fileViewModel,
                onOpen = { file -> onFileClick(file) },
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )

            AppScreen.SETTINGS -> SettingsScreen(
                settingsViewModel = settingsViewModel,
                onBack = { screen = AppScreen.FILES },
            )

            AppScreen.TEXT_EDITOR -> openedFile?.let { file ->
                TextEditorScreen(
                    viewModel = fileViewModel,
                    file = file,
                    onBack = {
                        screen = AppScreen.FILES
                        openedFilePath = null
                    },
                    onOpenWithThirdParty = onOpenWithThirdParty,
                )
            }

            AppScreen.IMAGE_VIEWER -> openedFile?.let { file ->
                ImageViewerScreen(
                    file = file,
                    onBack = {
                        screen = AppScreen.FILES
                        openedFilePath = null
                    },
                    onOpenWithThirdParty = onOpenWithThirdParty,
                )
            }
        }
    }
}

/**
 * 紧凑型侧栏项：相比 Material3 的 NavigationDrawerItem（固定 56dp 高），
 * 这里压到 44dp，图标与文字也更小，整体侧栏更省纵向空间。
 */
@Composable
private fun CompactDrawerItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
