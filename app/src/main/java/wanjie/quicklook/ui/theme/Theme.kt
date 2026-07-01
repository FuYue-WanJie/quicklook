package wanjie.quicklook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import wanjie.quicklook.data.AppSettings
import wanjie.quicklook.data.DarkThemeMode

@Composable
fun QuickLookTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (settings.darkTheme) {
        DarkThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.DARK -> true
    }

    val colors = when {
        // 动态取色开启且系统支持时，使用 Material You 壁纸取色
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        // 否则使用用户自选的种子色生成的配色
        darkTheme -> darkSchemeFromSeed(Color(settings.seedColor))
        else -> lightSchemeFromSeed(Color(settings.seedColor))
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
