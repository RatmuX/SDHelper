package io.github.ratmux.sdhelper.feature.deckeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.ActionCatalogCache
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.GridSize
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.ProfileStore
import io.github.ratmux.sdhelper.core.model.SlotPosition
import io.github.ratmux.sdhelper.ui.components.ButtonModel
import io.github.ratmux.sdhelper.ui.components.ButtonModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the last edit was not applied. */
enum class EditorMessage {
    /** The new grid would need more than the allowed number of pages for the existing buttons. */
    GRID_DOES_NOT_FIT,

    /** The target page has no free cell. */
    PAGE_FULL,

    /** The profile already has the largest allowed number of pages. */
    PAGE_LIMIT,
}

/** A grid change that moves buttons to other cells; the person must confirm it first. */
data class PendingResize(
    val grid: GridSize,
    val movedButtons: Int,
)

data class EditorUiState(
    val profile: DeckProfile? = null,
    val buttons: Map<String, ButtonModel> = emptyMap(),
    /** Every action the phone knows (remembered and live), for the action picker. */
    val actions: List<DeckAction> = emptyList(),
    val editMode: Boolean = false,
    val pendingResize: PendingResize? = null,
    val message: EditorMessage? = null,
    val connected: Boolean = false,
)

private class Live(
    val known: Map<String, DeckAction>,
    val liveIds: Set<String>,
    val states: Map<String, DeckActionState>,
    val connected: Boolean,
)

/**
 * The editor of the active layout. **Every change is ignored unless edit mode is on**, so opening the editor and
 * touching things can never alter a layout by accident. Changes are applied one at a time, always on top of the
 * latest saved layout, and saved immediately.
 */
class EditorViewModel(
    private val profiles: ProfileStore,
    cache: ActionCatalogCache,
    deck: DeckActions,
    status: ConnectionStatus,
    private val defaultPageName: (Int) -> String = { "Page $it" },
) : ViewModel() {
    private val editMode = MutableStateFlow(false)
    private val pending = MutableStateFlow<PendingResize?>(null)
    private val message = MutableStateFlow<EditorMessage?>(null)
    private val mutex = Mutex()

    private val layout =
        combine(profiles.profiles, profiles.activeProfileId) { all, activeId ->
            all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
        }

    private val live =
        combine(cache.actions, deck.actions, deck.states, status.state) { cached, current, states, connection ->
            Live(
                known = cached.associateBy { it.id } + current.associateBy { it.id },
                liveIds = current.map { it.id }.toSet(),
                states = states,
                connected = connection is ConnectionState.Connected,
            )
        }

    private val flags = combine(editMode, pending, message) { edit, resize, notice -> Triple(edit, resize, notice) }

    val state: StateFlow<EditorUiState> =
        combine(layout, live, flags) { profile, liveData, (edit, resize, notice) ->
            EditorUiState(
                profile = profile,
                buttons =
                    profile
                        ?.pages
                        ?.flatMap { it.slots }
                        ?.filterIsInstance<DeckSlot.Action>()
                        ?.associate { slot ->
                            slot.actionId to
                                ButtonModels.build(
                                    slot.actionId,
                                    liveData.known,
                                    liveData.states,
                                    liveData.liveIds,
                                    liveData.connected,
                                )
                        }.orEmpty(),
                actions = liveData.known.values.sortedBy { it.title.lowercase() },
                editMode = edit,
                pendingResize = resize,
                message = notice,
                connected = liveData.connected,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EditorUiState())

    fun setEditMode(on: Boolean) {
        editMode.value = on
        if (!on) pending.value = null
    }

    fun assign(
        at: SlotPosition,
        actionId: String,
    ) = edit { LayoutOps.setSlot(it, at, DeckSlot.Action(actionId)) }

    fun clear(at: SlotPosition) = edit { LayoutOps.clear(it, at) }

    fun move(
        from: SlotPosition,
        to: SlotPosition,
    ) = edit { LayoutOps.move(it, from, to) }

    /** Moves a button to the first free cell of [page]; the safe alternative to dragging across pages. */
    fun moveToPage(
        from: SlotPosition,
        page: Int,
    ) = edit { profile ->
        val free =
            profile.pages
                .getOrNull(page)
                ?.slots
                ?.indexOf(null) ?: return@edit null
        if (free < 0) {
            message.value = EditorMessage.PAGE_FULL
            null
        } else {
            LayoutOps.move(profile, from, SlotPosition(page, free))
        }
    }

    fun addPage() =
        edit { profile ->
            if (!LayoutOps.canAddPage(profile)) {
                message.value = EditorMessage.PAGE_LIMIT
                null
            } else {
                LayoutOps.addPage(profile, defaultPageName(profile.pages.size + 1))
            }
        }

    fun removePage(index: Int) = edit { LayoutOps.removePage(it, index) }

    fun renamePage(
        index: Int,
        name: String,
    ) = edit { LayoutOps.renamePage(it, index, name) }

    fun reorderPage(
        from: Int,
        to: Int,
    ) = edit { LayoutOps.reorderPage(it, from, to) }

    /**
     * Asks for a different grid size. A change that keeps every button in its cell is applied at once; one that has
     * to move buttons waits in [EditorUiState.pendingResize] for [confirmResize].
     */
    fun requestGrid(grid: GridSize) {
        if (!editMode.value) return
        viewModelScope.launch {
            mutex.withLock {
                val profile = currentProfile() ?: return@withLock
                val result = LayoutOps.resizeGrid(profile, grid, overflowPageName = defaultPageName)
                when {
                    result == null -> message.value = EditorMessage.GRID_DOES_NOT_FIT
                    result.movedButtons == 0 -> profiles.save(result.profile)
                    else -> pending.value = PendingResize(grid, result.movedButtons)
                }
            }
        }
    }

    fun confirmResize() {
        val request = pending.value ?: return
        pending.value = null
        edit { profile ->
            val result = LayoutOps.resizeGrid(profile, request.grid, overflowPageName = defaultPageName)
            if (result == null) message.value = EditorMessage.GRID_DOES_NOT_FIT
            result?.profile
        }
    }

    fun cancelResize() {
        pending.value = null
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun edit(change: (DeckProfile) -> DeckProfile?) {
        if (!editMode.value) return
        viewModelScope.launch {
            mutex.withLock {
                val profile = currentProfile() ?: return@withLock
                val updated = change(profile)
                if (updated != null && updated != profile) profiles.save(updated)
            }
        }
    }

    private suspend fun currentProfile(): DeckProfile? {
        val all = profiles.profiles.first()
        val activeId = profiles.activeProfileId.first()
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
