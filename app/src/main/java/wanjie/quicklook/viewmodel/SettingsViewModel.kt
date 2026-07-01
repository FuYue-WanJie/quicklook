package wanjie.quicklook.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import wanjie.quicklook.data.AppSettings
import wanjie.quicklook.data.DarkThemeMode
import wanjie.quicklook.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 维护应用设置的可观察状态：内存中的 [StateFlow] 即为单一数据源，
 * 每次变更都会同步写入 [SettingsRepository]，保证持久化。
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SettingsRepository.load(app))
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setShowHidden(value: Boolean) {
        _state.value = _state.value.copy(showHidden = value)
        SettingsRepository.saveShowHidden(getApplication(), value)
    }

    fun setDynamicColor(value: Boolean) {
        _state.value = _state.value.copy(dynamicColor = value)
        SettingsRepository.saveDynamicColor(getApplication(), value)
    }

    fun setSeedColor(value: Int) {
        _state.value = _state.value.copy(seedColor = value)
        SettingsRepository.saveSeedColor(getApplication(), value)
    }

    fun setDarkTheme(value: DarkThemeMode) {
        _state.value = _state.value.copy(darkTheme = value)
        SettingsRepository.saveDarkTheme(getApplication(), value)
    }
}
