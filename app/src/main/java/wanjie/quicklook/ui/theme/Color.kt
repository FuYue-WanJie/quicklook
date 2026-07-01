package wanjie.quicklook.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 静态色板：以 teal 为主色，避开蓝紫渐变，搭配中性 surface。
private val Teal10 = Color(0xFF00231F)
private val Teal20 = Color(0xFF00382F)
private val Teal30 = Color(0xFF005246)
private val Teal40 = Color(0xFF006B5B)
private val Teal80 = Color(0xFF4EDCC4)
private val Teal90 = Color(0xFF6CF9DF)

private val Neutral10 = Color(0xFF1A1C1B)
private val Neutral20 = Color(0xFF2F312F)
private val Neutral90 = Color(0xFFE2E3E0)
private val Neutral95 = Color(0xFFF2F3EF)
private val Neutral99 = Color(0xFFFBFDFA)

private val Amber80 = Color(0xFFFFB95C)
private val Amber90 = Color(0xFFFFD7A0)

val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Color(0xFF825500),
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Color(0xFF2A1800),
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral20,
    outline = Color(0xFF727871),
)

val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Teal80,
    onSecondary = Teal10,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Color(0xFF442C00),
    tertiaryContainer = Color(0xFF613F00),
    onTertiaryContainer = Amber90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    outline = Color(0xFF8B928B),
)
