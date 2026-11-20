package io.github.ratmux.sdhelper.feature.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.InvokeOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One action with its live state, ready to show. */
data class ActionRow(
    val action: DeckAction,
    val state: DeckActionState,
)

/** Why the last tap did not work. The computer's explanation is shown as it is. */
sealed interface ActionError {
    data object NotConnected : ActionError

    data class Refused(
        val message: String,
    ) : ActionError
}

data class ActionsUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val rows: List<ActionRow> = emptyList(),
    val error: ActionError? = null,
)

class ActionsViewModel(
    private val deck: DeckActions,
    status: ConnectionStatus,
) : ViewModel() {
    private val error = MutableStateFlow<ActionError?>(null)

    val state: StateFlow<ActionsUiState> =
        combine(status.state, deck.actions, deck.states, error) { connection, actions, states, lastError ->
            ActionsUiState(
                connection = connection,
                // An action without a reported state yet is shown as available; the computer corrects that at once.
                rows = actions.map { ActionRow(it, states[it.id] ?: DeckActionState()) },
                error = lastError,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ActionsUiState())

    fun invoke(row: ActionRow) {
        if (!row.state.available) return
        viewModelScope.launch {
            error.value =
                when (val outcome = deck.invoke(row.action.id)) {
                    InvokeOutcome.Success -> null
                    InvokeOutcome.NotConnected -> ActionError.NotConnected
                    is InvokeOutcome.Failure -> ActionError.Refused(outcome.message)
                }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
