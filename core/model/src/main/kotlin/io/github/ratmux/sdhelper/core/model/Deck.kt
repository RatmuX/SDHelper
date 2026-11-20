package io.github.ratmux.sdhelper.core.model

import kotlinx.coroutines.flow.StateFlow

/** An action the connected computer offers, with its title already in the phone's language. */
data class DeckAction(
    /** Opaque identifier, `providerId/actionId`. */
    val id: String,
    val title: String,
    /** Key of an icon in the built-in icon set, if the computer named one. */
    val icon: String?,
    val toggle: Boolean,
)

/** How a button should look, as a meaning; each design language maps it to its own colors. */
enum class DeckActionTone { NORMAL, ACTIVE, WARNING, ALERT }

data class DeckActionState(
    /** False when the action cannot work right now, for example no microphone is present. */
    val available: Boolean = true,
    val toggled: Boolean? = null,
    /** Short dynamic text such as a volume percentage or the track that is playing. */
    val label: String? = null,
    val tone: DeckActionTone = DeckActionTone.NORMAL,
)

sealed interface InvokeOutcome {
    data object Success : InvokeOutcome

    data object NotConnected : InvokeOutcome

    /** The computer refused or failed; [message] is its explanation. */
    data class Failure(
        val message: String,
    ) : InvokeOutcome
}

/** The actions of the connected computer and their live state. Implemented by `data:connection`. */
interface DeckActions {
    /** Empty while not connected. */
    val actions: StateFlow<List<DeckAction>>

    /** State by action id; kept current while connected. */
    val states: StateFlow<Map<String, DeckActionState>>

    suspend fun invoke(actionId: String): InvokeOutcome
}
