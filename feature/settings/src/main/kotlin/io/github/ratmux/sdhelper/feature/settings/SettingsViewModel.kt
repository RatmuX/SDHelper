package io.github.ratmux.sdhelper.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide options. Layouts are managed in the editor, where they are edited. */
class SettingsViewModel(
    private val settings: SettingsStore,
) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppSettings())

    fun setHapticFeedback(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(hapticFeedback = on) } }
    }

    fun setKeepScreenOn(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(keepScreenOn = on) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
