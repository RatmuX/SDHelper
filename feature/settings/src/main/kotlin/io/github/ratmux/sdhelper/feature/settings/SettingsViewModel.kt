package io.github.ratmux.sdhelper.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.LayoutIds
import io.github.ratmux.sdhelper.core.model.LayoutManager
import io.github.ratmux.sdhelper.core.model.ProfileStore
import io.github.ratmux.sdhelper.core.model.SettingsStore
import io.github.ratmux.sdhelper.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SettingsMessage {
    /** There must always be at least one layout, so the last one cannot be deleted. */
    LAST_LAYOUT_CANNOT_BE_DELETED,
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val profiles: List<DeckProfile> = emptyList(),
    val activeProfileId: String? = null,
    val message: SettingsMessage? = null,
)

/** The app-wide options (vibration, screen, theme) and the saved layouts of the Stream Deck. */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val profiles: ProfileStore,
    newId: () -> String = LayoutIds::random,
) : ViewModel() {
    private val manager = LayoutManager(profiles, newId)
    private val message = MutableStateFlow<SettingsMessage?>(null)

    val state: StateFlow<SettingsUiState> =
        combine(
            settings.settings,
            profiles.profiles,
            profiles.activeProfileId,
            message,
        ) { current, all, active, notice ->
            SettingsUiState(current, all, active ?: all.firstOrNull()?.id, notice)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun setHapticFeedback(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(hapticFeedback = on) } }
    }

    fun setKeepScreenOn(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(keepScreenOn = on) } }
    }

    /** Picks light or dark for good (until the person picks again); it no longer follows the phone. */
    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { settings.update { it.copy(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) } }
    }

    fun activate(profileId: String) {
        viewModelScope.launch { manager.activate(profileId) }
    }

    fun rename(
        profileId: String,
        name: String,
    ) {
        viewModelScope.launch { manager.rename(profileId, name) }
    }

    fun duplicate(profileId: String) {
        viewModelScope.launch { manager.saveCopy(profileId, "") }
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            if (!manager.delete(profileId)) message.value = SettingsMessage.LAST_LAYOUT_CANNOT_BE_DELETED
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
