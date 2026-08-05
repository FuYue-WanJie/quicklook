@file:OptIn(ExperimentalMaterial3Api::class)

package wanjie.quicklook.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import wanjie.quicklook.data.FileUtils
import wanjie.quicklook.data.WaveformLoader
import wanjie.quicklook.ui.widget.audio_seekbar.AudioWaveSlider
import wanjie.quicklook.ui.widget.audio_seekbar.WaveformAlignment

/**
 * 音频播放器：封面 + 标题/艺术家 + 波形进度条 + 播放控制。
 *
 * 波形进度条用 Amplituda 提取整首歌曲振幅数据后绘制，
 * 已播放部分着色、未播放部分为浅色，支持拖动 seek。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    path: String,
    displayName: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val audioUriStr = remember(path) {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            path
        } else {
            FileUtils.buildContentUri(context, path)?.toString() ?: "file://$path"
        }
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(audioUriStr))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // seek 拖动状态：拖动期间不跟随 ExoPlayer 位置，松手后恢复
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableStateOf(0f) }

    // 异步提取内嵌封面 + 标题/歌手元数据
    var artwork by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var metaTitle by remember { mutableStateOf<String?>(null) }
    var metaArtist by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(audioUriStr) {
        val meta = withContext(Dispatchers.IO) {
            FileUtils.extractAudioMetadata(context, audioUriStr)
        }
        metaTitle = meta.title
        metaArtist = meta.artist
        artwork = meta.artwork?.let { FileUtils.decodeSampledBitmap(it, 720) }?.asImageBitmap()
    }

    // 异步提取波形振幅数据（带缓存）
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    var waveformLoading by remember { mutableStateOf(true) }
    LaunchedEffect(audioUriStr) {
        waveformLoading = true
        amplitudes = withContext(Dispatchers.IO) {
            WaveformLoader.load(context, audioUriStr)
        }
        waveformLoading = false
    }

    // 播放位置轮询（500ms）
    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(500L)
        }
    }

    val realProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayProgress = if (isSeeking) seekProgress else realProgress

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(displayName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 封面
            val art = artwork
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(120.dp),
                )
            }

            // 标题 / 艺术家
            Text(
                text = metaTitle ?: displayName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            val artist = metaArtist
            if (artist != null) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // 波形进度条
            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                AudioWaveSlider(
                    progress = displayProgress,
                    amplitudes = amplitudes,
                    waveformAlignment = WaveformAlignment.Center,
                    waveformBrush = SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                    progressBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    onProgressChange = { v ->
                        isSeeking = true
                        seekProgress = v
                    },
                    onProgressChangeFinished = {
                        exoPlayer.seekTo((seekProgress * durationMs).toLong())
                        isSeeking = false
                    },
                    spikeWidth = 3.dp,
                    spikePadding = 1.dp,
                    spikeRadius = 2.dp,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
                // 波形提取中指示器
                if (waveformLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "分析波形…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 时间
            Text(
                text = "${FileUtils.formatDuration(positionMs)} / ${FileUtils.formatDuration(durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 播放/暂停
            IconButton(
                onClick = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
