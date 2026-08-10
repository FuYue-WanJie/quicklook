@file:OptIn(ExperimentalFoundationApi::class)

package wanjie.quicklook.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import wanjie.quicklook.R
import wanjie.quicklook.data.Bookmark
import wanjie.quicklook.data.FileCategory
import wanjie.quicklook.data.FileItem
import wanjie.quicklook.data.FileRepository
import wanjie.quicklook.ui.browser.BrowserScreen
import wanjie.quicklook.ui.browser.BrowserViewModel
import wanjie.quicklook.ui.components.FileListItem
import wanjie.quicklook.ui.viewer.AudioPlayerScreen
import wanjie.quicklook.ui.viewer.ArchiveViewerScreen
import wanjie.quicklook.ui.viewer.ImageViewerScreen
import wanjie.quicklook.ui.viewer.TextEditorScreen
import wanjie.quicklook.ui.viewer.VideoPlayerScreen
import wanjie.quicklook.ui.viewer.PdfViewerScreen
import wanjie.quicklook.ExternalCategory
import wanjie.quicklook.ExternalView
import java.util.Base64
import android.os.Environment
import kotlinx.coroutines.launch

private object Routes {
    const val BROWSER = "browser"
    const val RECENT = "recent"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val IMAGE = "image/{path}/{name}"
    const val VIDEO = "video/{path}/{name}"
    const val AUDIO = "audio/{path}/{name}"
    const val TEXT = "text/{path}/{name}"
    const val ARCHIVE = "archive/{path}/{name}"
    const val PDF = "pdf/{path}/{name}"
    fun image(path: String, name: String) = "image/${enc(path)}/${enc(name)}"
    fun video(path: String, name: String) = "video/${enc(path)}/${enc(name)}"
    fun audio(path: String, name: String) = "audio/${enc(path)}/${enc(name)}"
    fun text(path: String, name: String) = "text/${enc(path)}/${enc(name)}"
    fun archive(path: String, name: String) = "archive/${enc(path)}/${enc(name)}"
    fun pdf(path: String, name: String) = "pdf/${enc(path)}/${enc(name)}"
    // 使用 URL-safe Base64（无 +/、无填充），避免 / : 等字符破坏路由段匹配，
    // 也与 Navigation 是否自动解码无关，接收侧用 dec() 还原。
    private fun enc(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))

    fun dec(s: String): String =
        runCatching { String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8) }.getOrDefault(s)
}

/** 常用目录项：标签资源 + Environment 目录名 + 图标 */
private data class StandardDir(
    val labelRes: Int,
    val dirName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/** 抽屉分区分隔标签 */
@Composable
private fun DrawerSectionLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * 统一注册「路径 + 名称」两个 String 参数的查看器路由，4 个内置查看器共用。
 * path/name 经 URL-safe Base64 编码，这里解码后交给 [content]。
 */
private fun NavGraphBuilder.viewerRoute(
    route: String,
    navController: androidx.navigation.NavController,
    content: @Composable (path: String, name: String, onBack: () -> Unit) -> Unit,
) {
    composable(
        route = route,
        arguments = listOf(
            navArgument("path") { type = NavType.StringType },
            navArgument("name") { type = NavType.StringType },
        ),
    ) { entry ->
        content(
            Routes.dec(entry.arguments?.getString("path").orEmpty()),
            Routes.dec(entry.arguments?.getString("name").orEmpty()),
        ) { navController.popBackStack() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLookRoot(initialExternalView: ExternalView? = null) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 提升到根级，使抽屉与浏览器共享同一实例（书签列表、当前目录状态）
    val browserViewModel: BrowserViewModel = viewModel()
    val browserState by browserViewModel.uiState.collectAsStateWithLifecycle()

    // 接收到外部 ACTION_VIEW Intent 时，根据类别跳转到对应的全屏查看器 Screen
    LaunchedEffect(initialExternalView) {
        val ev = initialExternalView ?: return@LaunchedEffect
        val route = when (ev.category) {
            ExternalCategory.IMAGE -> Routes.image(ev.uri, ev.name)
            ExternalCategory.VIDEO -> Routes.video(ev.uri, ev.name)
            ExternalCategory.AUDIO -> Routes.audio(ev.uri, ev.name)
            ExternalCategory.TEXT -> Routes.text(ev.uri, ev.name)
        }
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    // 跟踪当前路由，用于抽屉项高亮
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val config = LocalConfiguration.current
            val drawerWidth = (config.screenWidthDp.dp * 0.82f).coerceAtMost(360.dp)
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth).fillMaxHeight(),
            ) {
                // ---- 标题区 ----
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider()

                val standardDirs = remember {
                    listOf(
                        StandardDir(R.string.dir_downloads, Environment.DIRECTORY_DOWNLOADS, Icons.Rounded.Download),
                        StandardDir(R.string.dir_dcim, Environment.DIRECTORY_DCIM, Icons.Rounded.PhotoCamera),
                        StandardDir(R.string.dir_pictures, Environment.DIRECTORY_PICTURES, Icons.Rounded.Image),
                        StandardDir(R.string.dir_music, Environment.DIRECTORY_MUSIC, Icons.Rounded.MusicNote),
                        StandardDir(R.string.dir_movies, Environment.DIRECTORY_MOVIES, Icons.Rounded.Movie),
                        StandardDir(R.string.dir_documents, Environment.DIRECTORY_DOCUMENTS, Icons.Rounded.Description),
                    ).filter { Environment.getExternalStoragePublicDirectory(it.dirName).exists() }
                }

                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                    // ---- 快捷区 ----
                    item { DrawerSectionLabel(R.string.drawer_section_shortcuts) }
                    item {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_storage)) },
                            selected = currentRoute == Routes.BROWSER,
                            onClick = {
                                closeDrawer()
                                navController.navigate(Routes.BROWSER) {
                                    popUpTo(Routes.BROWSER) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                    item {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_recent)) },
                            selected = currentRoute == Routes.RECENT,
                            onClick = {
                                closeDrawer()
                                navController.navigate(Routes.RECENT) {
                                    popUpTo(Routes.BROWSER) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }

                    // ---- 常用目录区 ----
                    if (standardDirs.isNotEmpty()) {
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                        item { DrawerSectionLabel(R.string.drawer_section_directories) }
                        items(standardDirs, key = { it.dirName }) { dir ->
                            val path = Environment.getExternalStoragePublicDirectory(dir.dirName).absolutePath
                            NavigationDrawerItem(
                                label = { Text(stringResource(dir.labelRes)) },
                                selected = currentRoute == Routes.BROWSER && path == browserState.currentPath,
                                onClick = {
                                    closeDrawer()
                                    if (currentRoute != Routes.BROWSER) {
                                        navController.navigate(Routes.BROWSER) {
                                            popUpTo(Routes.BROWSER) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    browserViewModel.selectBookmark(
                                        Bookmark(id = path, name = "", path = path, treeUri = "", isSaf = false)
                                    )
                                },
                                icon = { Icon(dir.icon, contentDescription = null) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // ---- 书签区 ----
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    item { DrawerSectionLabel(R.string.drawer_section_bookmarks) }
                    if (browserState.bookmarks.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.bookmarks_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                            )
                        }
                    } else {
                        items(browserState.bookmarks, key = { it.id }) { bookmark ->
                            NavigationDrawerItem(
                                label = { Text(bookmark.name, maxLines = 1) },
                                selected = !bookmark.isSaf && bookmark.path == browserState.currentPath,
                                onClick = {
                                    closeDrawer()
                                    if (currentRoute != Routes.BROWSER) {
                                        navController.navigate(Routes.BROWSER) {
                                            popUpTo(Routes.BROWSER) { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    browserViewModel.selectBookmark(bookmark)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (bookmark.isSaf) Icons.Rounded.Storage
                                        else Icons.Rounded.Bookmark,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // ---- 设置区 ----
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    item {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_settings)) },
                            selected = currentRoute == Routes.SETTINGS,
                            onClick = {
                                closeDrawer()
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(Routes.BROWSER) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                    item {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_about)) },
                            selected = currentRoute == Routes.ABOUT,
                            onClick = {
                                closeDrawer()
                                navController.navigate(Routes.ABOUT) {
                                    popUpTo(Routes.BROWSER) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.BROWSER,
            enterTransition = {
                fadeIn(tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
                )
            },
            exitTransition = {
                fadeOut(tween(200)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(200)
                )
            },
            popEnterTransition = {
                fadeIn(tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(300)
                )
            },
            popExitTransition = {
                fadeOut(tween(200)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(200)
                )
            },
        ) {
            composable(Routes.BROWSER) {
                BrowserScreen(
                    viewModel = browserViewModel,
                    onOpenDrawer = { openDrawer() },
                    onOpenImage = { path, name -> navController.navigate(Routes.image(path, name)) },
                    onOpenVideo = { path, name -> navController.navigate(Routes.video(path, name)) },
                    onOpenAudio = { path, name -> navController.navigate(Routes.audio(path, name)) },
                    onOpenText = { path, name -> navController.navigate(Routes.text(path, name)) },
                    onOpenArchive = { path, name -> navController.navigate(Routes.archive(path, name)) },
                    onOpenPdf = { path, name -> navController.navigate(Routes.pdf(path, name)) },
                )
            }
            composable(Routes.RECENT) {
                RecentScreen(
                    onOpenDrawer = { openDrawer() },
                    onOpenImage = { path, name -> navController.navigate(Routes.image(path, name)) },
                    onOpenVideo = { path, name -> navController.navigate(Routes.video(path, name)) },
                    onOpenAudio = { path, name -> navController.navigate(Routes.audio(path, name)) },
                    onOpenText = { path, name -> navController.navigate(Routes.text(path, name)) },
                    onOpenArchive = { path, name -> navController.navigate(Routes.archive(path, name)) },
                    onOpenPdf = { path, name -> navController.navigate(Routes.pdf(path, name)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenDrawer = { openDrawer() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onOpenDrawer = { openDrawer() })
            }
            viewerRoute(Routes.IMAGE, navController) { p, n, b -> ImageViewerScreen(p, n, b) }
            viewerRoute(Routes.VIDEO, navController) { p, n, b -> VideoPlayerScreen(p, n, b) }
            viewerRoute(Routes.AUDIO, navController) { p, n, b -> AudioPlayerScreen(p, n, b) }
            viewerRoute(Routes.TEXT, navController) { p, n, b -> TextEditorScreen(p, n, b) }
            viewerRoute(Routes.ARCHIVE, navController) { p, n, b ->
                ArchiveViewerScreen(
                    path = p,
                    displayName = n,
                    onBack = b,
                    onOpenImage = { path, name -> navController.navigate(Routes.image(path, name)) },
                    onOpenVideo = { path, name -> navController.navigate(Routes.video(path, name)) },
                    onOpenAudio = { path, name -> navController.navigate(Routes.audio(path, name)) },
                    onOpenText = { path, name -> navController.navigate(Routes.text(path, name)) },
                )
            }
            viewerRoute(Routes.PDF, navController) { p, n, b -> PdfViewerScreen(p, n, b) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentScreen(
    onOpenDrawer: () -> Unit,
    onOpenImage: (String, String) -> Unit = { _, _ -> },
    onOpenVideo: (String, String) -> Unit = { _, _ -> },
    onOpenAudio: (String, String) -> Unit = { _, _ -> },
    onOpenText: (String, String) -> Unit = { _, _ -> },
    onOpenArchive: (String, String) -> Unit = { _, _ -> },
    onOpenPdf: (String, String) -> Unit = { _, _ -> },
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val repo = FileRepository(context)
        files = repo.recent(100)
        loading = false
    }

    fun openFile(item: FileItem) {
        when (item.category) {
            FileCategory.IMAGE -> onOpenImage(item.path, item.name)
            FileCategory.VIDEO -> onOpenVideo(item.path, item.name)
            FileCategory.AUDIO -> onOpenAudio(item.path, item.name)
            FileCategory.TEXT, FileCategory.CODE -> onOpenText(item.path, item.name)
            FileCategory.ARCHIVE -> onOpenArchive(item.path, item.name)
            FileCategory.PDF -> onOpenPdf(item.path, item.name)
            else -> { /* 外部打开暂不支持，后续可扩展 */ }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_recent)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.drawer_open))
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (files.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(files, key = { it.uri.toString() + it.name }) { item ->
                    FileListItem(
                        item = item,
                        isSelected = false,
                        selectionMode = false,
                        onClick = { openFile(item) },
                        onLongClick = {},
                        modifier = Modifier.animateItemPlacement(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    viewModel: BrowserViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shizukuAvailable = viewModel.shizukuAvailable
    val shizukuGranted = viewModel.shizukuGranted

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.drawer_open))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.material3.ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_hidden)) },
                supportingContent = { Text(stringResource(R.string.settings_show_hidden_desc)) },
                trailingContent = {
                    Switch(
                        checked = state.showHidden,
                        onCheckedChange = { viewModel.setShowHidden(it) },
                    )
                },
            )
            HorizontalDivider()

            if (shizukuAvailable) {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_shizuku_title)) },
                    supportingContent = {
                        Text(
                            if (shizukuGranted) stringResource(R.string.settings_shizuku_granted)
                            else stringResource(R.string.settings_shizuku_not_granted)
                        )
                    },
                    trailingContent = {
                        if (!shizukuGranted) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.requestShizukuPermission() },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 4.dp),
                            ) {
                                Text(stringResource(R.string.settings_shizuku_request))
                            }
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    // 应用图标：加载系统合成的 adaptive icon，加载失败时回退占位图标
    val appIcon = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
                .toBitmap(192, 192)
                .asImageBitmap()
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_about)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.drawer_open))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon!!,
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (versionName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AboutRow(stringResource(R.string.about_author), stringResource(R.string.about_author_name))
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.about_copyright, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
