package wanjie.quicklook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 自选配色用的预设色板。每项为 ARGB 整数。
 * 关闭动态取色后，用户从这些色板中选择，主题由 [lightSchemeFromSeed]/[darkSchemeFromSeed]
 * 依据其色相生成。
 */
val SeedSwatches: List<Int> = listOf(
    0xFF006B5B.toInt(), // 青绿（默认）
    0xFF005AC1.toInt(), // 蓝
    0xFF6750A4.toInt(), // 紫
    0xFF9C4146.toInt(), // 红
    0xFFBC6813.toInt(), // 橙
    0xFF2E7D32.toInt(), // 绿
    0xFF00687E.toInt(), // 青
    0xFF8E2D9C.toInt(), // 品红
    0xFF5C6BC0.toInt(), // 靛蓝
    0xFFAD5E00.toInt(), // 琥珀
)

/** 由种子色派生浅色配色方案：取色相后按固定饱和度/明度生成一组协调色。 */
fun lightSchemeFromSeed(seed: Color): ColorScheme {
    val (h, _, _) = seed.toHsv()
    val tertiary = (h + 40f) % 360f
    return lightColorScheme(
        primary = Color.hsv(h, 0.55f, 0.45f),
        onPrimary = Color.White,
        primaryContainer = Color.hsv(h, 0.70f, 0.90f),
        onPrimaryContainer = Color.hsv(h, 0.55f, 0.20f),
        secondary = Color.hsv(h, 0.35f, 0.45f),
        onSecondary = Color.White,
        secondaryContainer = Color.hsv(h, 0.40f, 0.88f),
        onSecondaryContainer = Color.hsv(h, 0.35f, 0.20f),
        tertiary = Color.hsv(tertiary, 0.50f, 0.45f),
        onTertiary = Color.White,
        tertiaryContainer = Color.hsv(tertiary, 0.60f, 0.88f),
        onTertiaryContainer = Color.hsv(tertiary, 0.50f, 0.20f),
        background = Color.hsv(h, 0.20f, 0.98f),
        onBackground = Color.hsv(h, 0.10f, 0.12f),
        surface = Color.hsv(h, 0.20f, 0.98f),
        onSurface = Color.hsv(h, 0.10f, 0.12f),
        surfaceVariant = Color.hsv(h, 0.15f, 0.90f),
        onSurfaceVariant = Color.hsv(h, 0.10f, 0.32f),
        outline = Color.hsv(h, 0.05f, 0.50f),
    )
}

/** 由种子色派生深色配色方案。 */
fun darkSchemeFromSeed(seed: Color): ColorScheme {
    val (h, _, _) = seed.toHsv()
    val tertiary = (h + 40f) % 360f
    return darkColorScheme(
        primary = Color.hsv(h, 0.60f, 0.80f),
        onPrimary = Color.hsv(h, 0.55f, 0.18f),
        primaryContainer = Color.hsv(h, 0.55f, 0.30f),
        onPrimaryContainer = Color.hsv(h, 0.70f, 0.90f),
        secondary = Color.hsv(h, 0.40f, 0.80f),
        onSecondary = Color.hsv(h, 0.35f, 0.18f),
        secondaryContainer = Color.hsv(h, 0.35f, 0.30f),
        onSecondaryContainer = Color.hsv(h, 0.40f, 0.90f),
        tertiary = Color.hsv(tertiary, 0.55f, 0.80f),
        onTertiary = Color.hsv(tertiary, 0.50f, 0.18f),
        tertiaryContainer = Color.hsv(tertiary, 0.50f, 0.30f),
        onTertiaryContainer = Color.hsv(tertiary, 0.60f, 0.90f),
        background = Color.hsv(h, 0.10f, 0.10f),
        onBackground = Color.hsv(h, 0.10f, 0.90f),
        surface = Color.hsv(h, 0.10f, 0.10f),
        onSurface = Color.hsv(h, 0.10f, 0.90f),
        surfaceVariant = Color.hsv(h, 0.12f, 0.20f),
        onSurfaceVariant = Color.hsv(h, 0.10f, 0.80f),
        outline = Color.hsv(h, 0.05f, 0.60f),
    )
}

/** 将 Color 转为 HSV（色相、饱和度、明度），用于派生配色。 */
private fun Color.toHsv(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    var h = when {
        d == 0f -> 0f
        max == r -> ((g - b) / d) % 6f
        max == g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    if (h < 0f) h += 360f
    val s = if (max == 0f) 0f else d / max
    return Triple(h, s, max)
}
