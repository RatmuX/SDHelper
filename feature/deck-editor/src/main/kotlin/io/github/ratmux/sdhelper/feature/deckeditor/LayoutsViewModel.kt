package io.github.ratmux.sdhelper.feature.deckeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.DeckPage
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.GridSize
import io.github.ratmux.sdhelper.core.model.LayoutIds
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutManager
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LayoutsUiState(
    val profiles: List<DeckProfile> = emptyList(),
    val activeProfileId: String? = null,
)

/**
 * Management of whole layouts, done where layouts are edited: choose the active one, start an empty one or a
 * ready-made one under a name the person picks, save a copy, rename. (Deleting is in the settings.)
 */
class LayoutsViewModel(
    private val profiles: ProfileStore,
    private val language: () -> String = { "en" },
    private val defaultPageName: (Int) -> String = { "Page $it" },
    private val newId: () -> String = LayoutIds::random,
    private val newLayoutName: () -> String = { "New layout" },
) : ViewModel() {
    private val manager = LayoutManager(profiles, newId)

    val state: StateFlow<LayoutsUiState> =
        combine(profiles.profiles, profiles.activeProfileId) { all, active ->
            LayoutsUiState(all, active ?: all.firstOrNull()?.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LayoutsUiState())

    fun activate(profileId: String) {
        viewModelScope.launch { manager.activate(profileId) }
    }

    /** An empty layout with one page; the person fills it in the editor. It becomes the active layout. */
    fun createEmpty(name: String) {
        viewModelScope.launch { manager.createEmpty(name, newLayoutName(), defaultPageName(1)) }
    }

    /** A ready-made layout in the phone's language under [name] (its own title if blank); it becomes active. */
    fun addFromPreset(
        presetId: String,
        name: String,
    ) {
        viewModelScope.launch { manager.addFromPreset(presetId, name, language()) }
    }

    /** "Save": a copy of the layout under [name]; the active layout stays the one being edited. */
    fun saveCopy(
        profileId: String,
        name: String,
    ) {
        viewModelScope.launch { manager.saveCopy(profileId, name) }
    }

    fun rename(
        profileId: String,
        name: String,
    ) {
        viewModelScope.launch { manager.rename(profileId, name) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
