package io.github.ratmux.sdhelper.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.LayoutIds
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
import io.github.ratmux.sdhelper.core.model.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

/** App settings and the management of layouts: choose the active one, add a ready-made one, copy, rename, delete. */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val profiles: ProfileStore,
    private val language: () -> String = { "en" },
    private val newId: () -> String = LayoutIds::random,
) : ViewModel() {
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

    fun activate(profileId: String) {
        viewModelScope.launch { profiles.setActive(profileId) }
    }

    /** Adds a ready-made layout in the phone's language; it does not become active until chosen. */
    fun addFromPreset(presetId: String) {
        val preset = LayoutPresets.byId(presetId) ?: return
        viewModelScope.launch {
            val created = preset.create(language(), newId = newId)
            profiles.save(created.copy(name = uniqueName(created.name)))
        }
    }

    fun duplicate(profileId: String) {
        viewModelScope.launch {
            val original = profiles.profiles.first().firstOrNull { it.id == profileId } ?: return@launch
            profiles.save(
                original.copy(
                    id = newId(),
                    name = uniqueName(original.name),
                    pages = original.pages.map { it.copy(id = newId()) },
                ),
            )
        }
    }

    fun rename(
        profileId: String,
        name: String,
    ) {
        viewModelScope.launch {
            val original = profiles.profiles.first().firstOrNull { it.id == profileId } ?: return@launch
            val renamed = LayoutOps.renameProfile(original, name)
            if (renamed != original) profiles.save(renamed)
        }
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            if (!profiles.delete(profileId)) message.value = SettingsMessage.LAST_LAYOUT_CANNOT_BE_DELETED
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /** `Gaming`, then `Gaming (2)`, `Gaming (3)`, ... and always within the name length limit. */
    private suspend fun uniqueName(base: String): String {
        val taken =
            profiles.profiles
                .first()
                .map { it.name }
                .toSet()
        if (base !in taken) return base
        var number = 2
        while (true) {
            val suffix = " ($number)"
            val candidate = base.take(LayoutLimits.MAX_NAME_LENGTH - suffix.length) + suffix
            if (candidate !in taken) return candidate
            number++
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
