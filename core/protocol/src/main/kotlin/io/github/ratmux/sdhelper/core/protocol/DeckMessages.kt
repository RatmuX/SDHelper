package io.github.ratmux.sdhelper.core.protocol

import kotlinx.serialization.Serializable

/** Message types of the `deck` channel, see docs/PROTOCOL.md section 4. */
object DeckTypes {
    const val CATALOG_GET = "catalog.get"
    const val CATALOG = "catalog"
    const val INVOKE = "invoke"
    const val RESULT = "result"
    const val STATE = "state"
}

/** One action in the catalog. [id] is `providerId/actionId`. */
@Serializable
data class DeckActionInfo(
    val id: String,
    val title: Map<String, String>,
    val icon: String? = null,
    val toggle: Boolean = false,
)

@Serializable
data class Catalog(
    val actions: List<DeckActionInfo>,
)

/** State as sent over the wire; [tone] is the name of an `ActionTone`, unknown names mean `NORMAL`. */
@Serializable
data class WireActionState(
    val available: Boolean = true,
    val toggled: Boolean? = null,
    val label: String? = null,
    val tone: String = "NORMAL",
)

@Serializable
data class StateUpdate(
    val actionId: String,
    val state: WireActionState,
)

@Serializable
data class Invoke(
    val actionId: String,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class InvokeResult(
    val ok: Boolean,
    val error: String? = null,
)

/** A message on the `deck` channel. */
inline fun <reified T> deckMessage(
    type: String,
    payload: T,
    id: String? = null,
): Envelope = message(Channels.DECK, type, payload, id)
