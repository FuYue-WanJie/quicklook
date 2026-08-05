package wanjie.quicklook.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import wanjie.quicklook.data.FileUtils

/**
 * 全屏视频播放器（Composable，不是 Dialog）。
 * - 通过 FileProvider content URI 加载视频
 * - 使用 Media3 PlayerView 默认控制器
 * - 顶部叠加返回按钮 + 文件名（与控制器同步显隐）
 */
@Composable
fun VideoPlayerScreen(
    path: String,
    displayName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // path 可以是绝对路径或 content:// URI（外部 Intent）
    val videoUriStr = remember(path) {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            path
        } else {
            FileUtils.buildContentUri(context, path)?.toString() ?: "file://$path"
        }
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUriStr))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // 跟随 PlayerView 默认控制器的显隐
    var controlsVisible by remember { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    val statusBarHeight = with(density) {
        val rawTop = WindowInsets.statusBars.getTop(density)
        if (rawTop > 0) rawTop.toDp() else 24.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // PlayerView 全屏 + Media3 默认控制器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    controllerAutoShow = true
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    // 监听控制器显隐，让顶部返回栏跟随
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 顶部叠加：返回按钮 + 文件名，跟随控制器显隐
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(top = statusBarHeight, bottom = 8.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = displayName,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                )
            }
        }
    }
}
