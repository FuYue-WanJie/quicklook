package wanjie.quicklook.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import wanjie.quicklook.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Typed settings façade over DataStore. No repository object — keep the surface narrow.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = intPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SORT_ORDER = intPreferencesKey("sort_order")
        val FOLDERS_FIRST = booleanPreferencesKey("folders_first")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        val idx = p[Keys.THEME] ?: ThemeMode.System.ordinal
        ThemeMode.entries.getOrElse(idx) { ThemeMode.System }
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC] ?: true }
    val showHidden: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_HIDDEN] ?: false }

    val sortConfig: Flow<SortConfig> = context.dataStore.data.map { p ->
        SortConfig(
            order = SortOrder.entries.getOrElse(p[Keys.SORT_ORDER] ?: 0) { SortOrder.NAME_ASC },
            foldersFirst = p[Keys.FOLDERS_FIRST] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.ordinal }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC] = enabled }
    }

    suspend fun setShowHidden(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_HIDDEN] = enabled }
    }

    suspend fun setSortConfig(config: SortConfig) {
        context.dataStore.edit {
            it[Keys.SORT_ORDER] = config.order.ordinal
            it[Keys.FOLDERS_FIRST] = config.foldersFirst
        }
    }
}
