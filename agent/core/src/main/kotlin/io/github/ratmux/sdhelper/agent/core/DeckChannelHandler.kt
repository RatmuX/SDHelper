package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.pluginapi.ActionResult
import io.github.ratmux.sdhelper.core.pluginapi.ActionState
import io.github.ratmux.sdhelper.core.protocol.Catalog
import io.github.ratmux.sdhelper.core.protocol.Channels
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.DeckTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.ErrorCodes
import io.github.ratmux.sdhelper.core.protocol.ErrorPayload
import io.github.ratmux.sdhelper.core.protocol.Invoke
import io.github.ratmux.sdhelper.core.protocol.InvokeResult
import io.github.ratmux.sdhelper.core.protocol.StateUpdate
import io.github.ratmux.sdhelper.core.protocol.WireActionState
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.deckMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArraySet
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The `deck` channel (docs/PROTOCOL.md section 4): publishes the action catalog, runs actions and pushes state
 * changes to every connected phone. Only authenticated devices reach it; action ids are looked up in the
 * [ProviderRegistry], nothing from the phone is ever executed as a command line.
 */
class DeckChannelHandler(
    private val registry: ProviderRegistry,
    scope: CoroutineScope,
    private val invokeTimeoutMillis: Long = INVOKE_TIMEOUT_MILLIS,
) : ChannelHandler {
    override val channel: String = Channels.DECK

    private val sessions = CopyOnWriteArraySet<SessionHandle>()
    private val states = registry.states(scope)

    init {
        scope.launch {
            var published = emptyMap<String, ActionState>()
            states.collect { now ->
                now.forEach { (id, state) -> if (published[id] != state) broadcast(stateMessage(id, state)) }
                published = now
            }
        }
    }

    override suspend fun handle(
        device: PairedDevice,
        message: Envelope,
    ): List<Envelope> =
        when (message.type) {
            DeckTypes.CATALOG_GET -> {
                listOf(deckMessage(DeckTypes.CATALOG, Catalog(registry.catalog()), message.id)) +
                    states.value.map { (id, state) -> stateMessage(id, state) }
            }

            DeckTypes.INVOKE -> {
                listOf(deckMessage(DeckTypes.RESULT, invoke(message), message.id))
            }

            else -> {
                listOf(
                    controlMessage(
                        ControlTypes.ERROR,
                        ErrorPayload(ErrorCodes.UNSUPPORTED_TYPE, "deck/${message.type}"),
                        message.id,
                    ),
                )
            }
        }

    override fun onSessionStarted(session: SessionHandle) {
        sessions += session
    }

    override fun onSessionEnded(session: SessionHandle) {
        sessions -= session
    }

    private suspend fun invoke(message: Envelope): InvokeResult {
        val request =
            runCatching { message.payloadAs<Invoke>() }.getOrNull() ?: return InvokeResult(false, "malformed request")
        if (request.params.size > MAX_PARAMS ||
            request.params.any { (k, v) -> k.length > MAX_TEXT || v.length > MAX_TEXT }
        ) {
            return InvokeResult(false, "invalid parameters")
        }
        val entry = registry.find(request.actionId) ?: return InvokeResult(false, "unknown action")

        val result =
            try {
                withTimeoutOrNull(invokeTimeoutMillis) { entry.provider.invoke(entry.localId, request.params) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.log(Level.WARNING, "Action ${request.actionId} failed", e)
                ActionResult.Failure("action failed")
            }
        return when (result) {
            null -> InvokeResult(false, "timed out")
            ActionResult.Success -> InvokeResult(true)
            is ActionResult.Failure -> InvokeResult(false, result.message)
        }
    }

    private fun broadcast(message: Envelope) {
        sessions.forEach { it.sink.trySend(message) }
    }

    private fun stateMessage(
        actionId: String,
        state: ActionState,
    ) = deckMessage(
        DeckTypes.STATE,
        StateUpdate(actionId, WireActionState(state.available, state.toggled, state.label, state.tone.name)),
    )

    private companion object {
        const val INVOKE_TIMEOUT_MILLIS = 10_000L
        const val MAX_PARAMS = 16
        const val MAX_TEXT = 256
        val LOG: Logger = Logger.getLogger(DeckChannelHandler::class.java.name)
    }
}
