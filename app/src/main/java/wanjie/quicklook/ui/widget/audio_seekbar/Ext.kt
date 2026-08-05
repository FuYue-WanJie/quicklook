package wanjie.quicklook.ui.widget.audio_seekbar

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 将原始振幅列表重采样为目标柱数，并归一化到 [minHeight, maxHeight]。
 * 移植自 SPICaMusic，简化后仅保留 Avg 聚合（最常用）。
 */
internal fun List<Int>.toDrawableAmplitudes(
    amplitudeType: AmplitudeType,
    spikes: Int,
    minHeight: Float,
    maxHeight: Float,
): List<Float> {
    val amplitudes = map { it * 1f }
    if (amplitudes.isEmpty() || spikes == 0) {
        return List(spikes) { minHeight.coerceAtLeast(20f) }
    }
    val transform = { data: List<Float> ->
        when (amplitudeType) {
            AmplitudeType.Avg -> data.average()
            AmplitudeType.Max -> data.max()
            AmplitudeType.Min -> data.min()
        }.toFloat().coerceIn(minHeight, maxHeight)
    }
    val res = when {
        spikes > amplitudes.count() -> amplitudes.fillToSize(spikes, transform)
        else -> amplitudes.chunkToSize(spikes, transform)
    }.normalize(minHeight, maxHeight)
    return res
}

internal fun <T> Iterable<T>.fillToSize(
    size: Int,
    transform: (List<T>) -> T,
): List<T> {
    val capacity = ceil(count().safeDiv(size)).roundToInt()
    return map { data -> List(capacity) { data } }.flatten().chunkToSize(size, transform)
}

internal fun <T> Iterable<T>.chunkToSize(
    size: Int,
    transform: (List<T>) -> T,
): List<T> {
    if (size <= 0) return emptyList()
    val chunkSize = count() / size
    if (chunkSize <= 0) return take(size)
    val remainder = count() % size
    val remainderIndex = if (remainder > 0) ceil(count().safeDiv(remainder)).roundToInt() else 0
    val chunkIteration = filterIndexed { index, _ ->
        remainderIndex == 0 || index % remainderIndex != 0
    }.chunked(chunkSize, transform)
    return when (size) {
        chunkIteration.count() -> chunkIteration
        else -> chunkIteration.chunkToSize(size, transform)
    }
}

internal fun Iterable<Float>.normalize(min: Float, max: Float): List<Float> =
    map { (max - min) * ((it - min()) safeDiv (max() - min())) + min }

private fun Int.safeDiv(value: Int): Float =
    if (value == 0) 0f else this / value.toFloat()

private infix fun Float.safeDiv(value: Float): Float =
    if (value == 0f) 0f else this / value
