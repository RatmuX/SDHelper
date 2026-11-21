package io.github.ratmux.sdhelper.feature.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.ActionCatalogCache
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.InvokeOutcome
import io.github.ratmux.sdhelper.core.model.ProfileStore
import io.github.ratmux.sdhelper.core.model.SettingsStore
import io.github.ratmux.sdhelper.ui.components.ButtonModel
import io.github.ratmux.sdhelper.ui.components.ButtonModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A short notice about the last button press that did not work. */
sealed interface DeckMessage {
    data object NotConnected : DeckMessage

    /** The computer refused or failed; its explanation is shown as it is. */
    data class Refused(
        val message: String,
    ) : DeckMessage
}

data class DeckUiState(
    /** The active layout, or null for a moment at the very first start. */
    val profile: DeckProfile? = null,
    /** Every button used by the active layout, by action id. */
    val buttons: Map<String, ButtonModel> = emptyMap(),
    val connection: ConnectionState = ConnectionState.Disconnected,
    val settings: AppSettings = AppSettings(),
    val message: DeckMessage? = null,
)

private class Live(
    val known: Map<String, DeckAction>,
    val liveIds: Set<String>,
    val states: Map<String, DeckActionState>,
    val connection: ConnectionState,
)

class DeckViewModel(
    private val profiles: ProfileStore,
    cache: ActionCatalogCache,
    private val deck: DeckActions,
    status: ConnectionStatus,
    settings: SettingsStore,
) : ViewModel() {
    private val message = MutableStateFlow<DeckMessage?>(null)

    private val layout =
        combine(profiles.profiles, profiles.activeProfileId) { all, activeId ->
            all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
        }

    private val live =
        combine(cache.actions, deck.actions, deck.states, status.state) { cached, current, states, connection ->
            // what the computer offers right now overrides what was remembered
            Live(
                known = cached.associateBy { it.id } + current.associateBy { it.id },
                liveIds = current.map { it.id }.toSet(),
                states = states,
                connection = connection,
            )
        }

    val state: StateFlow<DeckUiState> =
        combine(layout, live, settings.settings, message) { profile, liveData, appSettings, notice ->
            DeckUiState(
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
                                    connected = liveData.connection is ConnectionState.Connected,
                                )
                        }.orEmpty(),
                connection = liveData.connection,
                settings = appSettings,
                message = notice,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DeckUiState())

    fun press(actionId: String) {
        viewModelScope.launch {
            message.value =
                when (val outcome = deck.invoke(actionId)) {
                    InvokeOutcome.Success -> null
                    InvokeOutcome.NotConnected -> DeckMessage.NotConnected
                    is InvokeOutcome.Failure -> DeckMessage.Refused(outcome.message)
                }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
