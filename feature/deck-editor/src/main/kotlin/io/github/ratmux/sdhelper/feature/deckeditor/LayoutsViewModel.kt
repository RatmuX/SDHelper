package io.github.ratmux.sdhelper.feature.deckeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.DeckPage
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.GridSize
import io.github.ratmux.sdhelper.core.model.LayoutIds
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LayoutsMessage {
    /** There must always be at least one layout, so the last one cannot be deleted. */
    LAST_LAYOUT_CANNOT_BE_DELETED,
}

data class LayoutsUiState(
    val profiles: List<DeckProfile> = emptyList(),
    val activeProfileId: String? = null,
    val message: LayoutsMessage? = null,
)

/**
 * Management of whole layouts, done where layouts are edited: choose the active one, start an empty one or a
 * ready-made one under a name the person picks, copy, rename, delete.
 */
class LayoutsViewModel(
    private val profiles: ProfileStore,
    private val language: () -> String = { "en" },
    private val defaultPageName: (Int) -> String = { "Page $it" },
    private val newId: () -> String = LayoutIds::random,
    private val newLayoutName: () -> String = { "New layout" },
) : ViewModel() {
    private val message = MutableStateFlow<LayoutsMessage?>(null)

    val state: StateFlow<LayoutsUiState> =
        combine(profiles.profiles, profiles.activeProfileId, message) { all, active, notice ->
            LayoutsUiState(all, active ?: all.firstOrNull()?.id, notice)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LayoutsUiState())

    fun activate(profileId: String) {
        viewModelScope.launch { profiles.setActive(profileId) }
    }

    /** An empty layout with one page; the person fills it in the editor. It becomes the active layout. */
    fun createEmpty(name: String) {
        viewModelScope.launch {
            val grid = GridSize.DEFAULT
            val page = DeckPage(newId(), defaultPageName(1), LayoutOps.emptySlots(grid))
            val created = DeckProfile(newId(), chosenName(name, fallback = newLayoutName()), grid, listOf(page))
            profiles.save(created.copy(name = uniqueName(created.name)))
            profiles.setActive(created.id)
        }
    }

    /** A ready-made layout in the phone's language under [name] (its own title if blank); it becomes active. */
    fun addFromPreset(
        presetId: String,
        name: String,
    ) {
        val preset = LayoutPresets.byId(presetId) ?: return
        viewModelScope.launch {
            val created = preset.create(language(), newId = newId)
            val named = created.copy(name = uniqueName(chosenName(name, fallback = created.name)))
            profiles.save(named)
            profiles.setActive(named.id)
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
            if (!profiles.delete(profileId)) message.value = LayoutsMessage.LAST_LAYOUT_CANNOT_BE_DELETED
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun chosenName(
        raw: String,
        fallback: String,
    ): String = LayoutOps.cleanName(raw) ?: fallback

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
