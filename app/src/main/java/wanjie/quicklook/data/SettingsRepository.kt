package wanjie.quicklook.data

import android.content.Context
import android.content.SharedPreferences

/** 深色模式偏好。 */
enum class DarkThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

/**
 * 应用设置集合。所有字段都会被持久化到 SharedPreferences，
 * 重启后保持一致。
 */
data class AppSettings(
    val showHidden: Boolean = false,
    val dynamicColor: Boolean = true,
    val seedColor: Int = DEFAULT_SEED,
    val darkTheme: DarkThemeMode = DarkThemeMode.FOLLOW_SYSTEM,
) {
    companion object {
        /** 默认种子色：与原 teal 主色接近。 */
        const val DEFAULT_SEED = 0xFF006B5B.toInt()
    }
}

/**
 * 轻量设置仓库，基于 SharedPreferences。
 * ViewModel 在内存中以 StateFlow 维护当前值，变更时同步落盘。
 */
object SettingsRepository {
    private const val PREFS = "file_manager_settings"
    private const val KEY_SHOW_HIDDEN = "show_hidden"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_SEED_COLOR = "seed_color"
    private const val KEY_DARK_THEME = "dark_theme"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val p = prefs(context)
        return AppSettings(
            showHidden = p.getBoolean(KEY_SHOW_HIDDEN, false),
            dynamicColor = p.getBoolean(KEY_DYNAMIC_COLOR, true),
            seedColor = p.getInt(KEY_SEED_COLOR, AppSettings.DEFAULT_SEED),
            darkTheme = DarkThemeMode.entries.getOrElse(p.getInt(KEY_DARK_THEME, 0)) {
                DarkThemeMode.FOLLOW_SYSTEM
            },
        )
    }

    fun saveShowHidden(context: Context, value: Boolean) =
        edit(context) { it.putBoolean(KEY_SHOW_HIDDEN, value) }

    fun saveDynamicColor(context: Context, value: Boolean) =
        edit(context) { it.putBoolean(KEY_DYNAMIC_COLOR, value) }

    fun saveSeedColor(context: Context, value: Int) =
        edit(context) { it.putInt(KEY_SEED_COLOR, value) }

    fun saveDarkTheme(context: Context, value: DarkThemeMode) =
        edit(context) { it.putInt(KEY_DARK_THEME, value.ordinal) }

    private inline fun edit(context: Context, block: (SharedPreferences.Editor) -> Unit) {
        val editor = prefs(context).edit()
        block(editor)
        editor.apply()
    }
}
