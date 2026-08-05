package wanjie.quicklook.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = md_primary_light, onPrimary = md_on_primary_light,
    primaryContainer = md_primary_container_light, onPrimaryContainer = md_on_primary_container_light,
    secondary = md_secondary_light, onSecondary = md_on_secondary_light,
    secondaryContainer = md_secondary_container_light, onSecondaryContainer = md_on_secondary_container_light,
    tertiary = md_tertiary_light, onTertiary = md_on_tertiary_light,
    tertiaryContainer = md_tertiary_container_light, onTertiaryContainer = md_on_tertiary_container_light,
    error = md_error_light, onError = md_on_error_light,
    errorContainer = md_error_container_light, onErrorContainer = md_on_error_container_light,
    background = md_background_light, onBackground = md_on_background_light,
    surface = md_surface_light, onSurface = md_on_surface_light,
    surfaceVariant = md_surface_variant_light, onSurfaceVariant = md_on_surface_variant_light,
    outline = md_outline_light, outlineVariant = md_outline_variant_light,
    surfaceContainerLowest = md_surface_container_lowest_light,
    surfaceContainerLow = md_surface_container_low_light,
    surfaceContainer = md_surface_container_light,
    surfaceContainerHigh = md_surface_container_high_light,
    surfaceContainerHighest = md_surface_container_highest_light,
)

private val DarkColors = darkColorScheme(
    primary = md_primary_dark, onPrimary = md_on_primary_dark,
    primaryContainer = md_primary_container_dark, onPrimaryContainer = md_on_primary_container_dark,
    secondary = md_secondary_dark, onSecondary = md_on_secondary_dark,
    secondaryContainer = md_secondary_container_dark, onSecondaryContainer = md_on_secondary_container_dark,
    tertiary = md_tertiary_dark, onTertiary = md_on_tertiary_dark,
    tertiaryContainer = md_tertiary_container_dark, onTertiaryContainer = md_on_tertiary_container_dark,
    error = md_error_dark, onError = md_on_error_dark,
    errorContainer = md_error_container_dark, onErrorContainer = md_on_error_container_dark,
    background = md_background_dark, onBackground = md_on_background_dark,
    surface = md_surface_dark, onSurface = md_on_surface_dark,
    surfaceVariant = md_surface_variant_dark, onSurfaceVariant = md_on_surface_variant_dark,
    outline = md_outline_dark, outlineVariant = md_outline_variant_dark,
    surfaceContainerLowest = md_surface_container_lowest_dark,
    surfaceContainerLow = md_surface_container_low_dark,
    surfaceContainer = md_surface_container_dark,
    surfaceContainerHigh = md_surface_container_high_dark,
    surfaceContainerHighest = md_surface_container_highest_dark,
)

enum class ThemeMode { System, Light, Dark }

@Composable
fun QuickLookTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
