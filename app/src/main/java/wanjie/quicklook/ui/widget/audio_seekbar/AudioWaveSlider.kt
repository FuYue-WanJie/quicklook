@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package wanjie.quicklook.ui.widget.audio_seekbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val MinSpikeWidthDp: Dp = 1.dp
private val MaxSpikeWidthDp: Dp = 24.dp
private val MinSpikePaddingDp: Dp = 0.dp
private val MaxSpikePaddingDp: Dp = 12.dp
private val MinSpikeRadiusDp: Dp = 0.dp
private val MaxSpikeRadiusDp: Dp = 12.dp

private const val MinProgress: Float = 0F
private const val MaxProgress: Float = 1F

private val MinSpikeHeight: Float = 1f
private const val DefaultGraphicsLayerAlpha: Float = .99F

/**
 * 音频波形进度条（静态波形）。
 *
 * 用法：传入预提取的 [amplitudes] 和当前 [progress]（0~1），
 * 已播放部分会以 [progressBrush] 着色，未播放部分用 [waveformBrush]。
 *
 * 核心技巧：先画完整波形，再用 `BlendMode.SrcAtop` 叠加一个宽度=进度的进度色矩形，
 * 只保留已播放区域的颜色。
 *
 * @param amplitudes 预提取的振幅列表（Amplituda 结果），为空时回退为均匀柱
 * @param progress 当前播放进度 0..1
 * @param onProgressChange 拖动时回调
 * @param onProgressChangeFinished 松手时回调
 */
@Composable
fun AudioWaveSlider(
    modifier: Modifier = Modifier,
    style: DrawStyle = Fill,
    waveformBrush: Brush = SolidColor(Color.White),
    progressBrush: Brush = SolidColor(Color.Blue),
    waveformAlignment: WaveformAlignment = WaveformAlignment.Center,
    amplitudeType: AmplitudeType = AmplitudeType.Avg,
    onProgressChangeFinished: (() -> Unit)? = null,
    spikeAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = tween(500),
    spikeWidth: Dp = 4.dp,
    spikeRadius: Dp = 2.dp,
    spikePadding: Dp = 1.dp,
    progress: Float = 0F,
    amplitudes: List<Int>,
    onProgressChange: (Float) -> Unit,
) {
    val progress = animateFloatAsState(
        progress.coerceIn(MinProgress, MaxProgress),
        tween(65, easing = LinearEasing),
        label = "wave_progress",
    ).value

    val spikeWidth = remember(spikeWidth) { spikeWidth.coerceIn(MinSpikeWidthDp, MaxSpikeWidthDp) }
    val spikePadding = remember(spikePadding) { spikePadding.coerceIn(MinSpikePaddingDp, MaxSpikePaddingDp) }
    val spikeRadius = remember(spikeRadius) { spikeRadius.coerceIn(MinSpikeRadiusDp, MaxSpikeRadiusDp) }
    val spikeTotalWidth = remember(spikeWidth, spikePadding) {
        derivedStateOf { spikeWidth + spikePadding }
    }.value

    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }
    var spikes by remember { mutableFloatStateOf(0F) }

    val spikesAmplitudes = remember(amplitudes, spikes, amplitudeType) {
        amplitudes.toDrawableAmplitudes(
            amplitudeType = amplitudeType,
            spikes = spikes.toInt(),
            minHeight = MinSpikeHeight,
            maxHeight = canvasSize.height.coerceAtLeast(MinSpikeHeight),
        )
    }.map { animateFloatAsState(it, spikeAnimationSpec, label = "spike").value }

    val lastTickTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val coroutineScope = rememberCoroutineScope()
    var isTouch by remember { mutableStateOf(false) }

    val scaleY = animateFloatAsState(
        if (isTouch) 0.8f else 1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "scaleY",
    )

    val thumbWidth = animateDpAsState(
        if (isTouch) 40.dp else 0.dp,
        spring(dampingRatio = 0.9f, stiffness = 900f),
        label = "thumb",
    )

    Slider(
        modifier = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        value = progress,
        thumb = {},
        track = {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .scale(1f, scaleY.value)
                    .graphicsLayer(alpha = DefaultGraphicsLayerAlpha),
            ) {
                canvasSize = size
                spikes = size.width / spikeTotalWidth.toPx()
                spikesAmplitudes.forEachIndexed { index, amplitude ->
                    drawRoundRect(
                        brush = waveformBrush,
                        topLeft = Offset(
                            x = index * spikeTotalWidth.toPx(),
                            y = when (waveformAlignment) {
                                WaveformAlignment.Top -> 0F
                                WaveformAlignment.Bottom -> size.height - amplitude
                                WaveformAlignment.Center -> size.height / 2F - amplitude / 2F
                            },
                        ),
                        size = Size(width = spikeWidth.toPx(), height = amplitude),
                        cornerRadius = CornerRadius(spikeRadius.toPx(), spikeRadius.toPx()),
                        style = style,
                    )
                }
                // 已播放部分着色：用 SrcAtop 只覆盖已绘制像素的左侧区域
                drawRect(
                    brush = progressBrush,
                    size = Size(width = progress * size.width, height = size.height),
                    blendMode = BlendMode.SrcAtop,
                )
            }
            if (thumbWidth.value > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(LocalDensity.current) {
                                (progress * canvasSize.width).toDp() - thumbWidth.value / 2
                            },
                        )
                        .width(thumbWidth.value)
                        .fillMaxHeight(),
                )
            }
        },
        valueRange = MinProgress..MaxProgress,
        onValueChange = {
            onProgressChange(it)
            coroutineScope.launch(Dispatchers.IO) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTickTime.longValue < 60) return@launch
                lastTickTime.longValue = currentTime
            }
            isTouch = true
        },
        onValueChangeFinished = {
            onProgressChangeFinished?.invoke()
            isTouch = false
        },
    )
}
