package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.core.model.ActionCatalogCache
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActionTone
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.InvokeOutcome
import io.github.ratmux.sdhelper.core.protocol.Catalog
import io.github.ratmux.sdhelper.core.protocol.Channels
import io.github.ratmux.sdhelper.core.protocol.DeckTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.Invoke
import io.github.ratmux.sdhelper.core.protocol.InvokeResult
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.StateUpdate
import io.github.ratmux.sdhelper.core.protocol.deckMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What [DefaultDeckActions] needs from the connection: messages in, messages out. */
interface DeckTransport {
    val incoming: SharedFlow<Envelope>

    /** Sends on the current connection; false when there is none. */
    fun send(envelope: Envelope): Boolean
}

/**
 * Phone side of the `deck` channel (docs/PROTOCOL.md section 4): asks for the catalog whenever a connection is
 * established, keeps the state the computer pushes, and runs actions. Everything is cleared when the connection ends.
 */
class DefaultDeckActions(
    status: ConnectionStatus,
    private val transport: DeckTransport,
    private val scope: CoroutineScope,
    private val language: () -> String = { Locale.getDefault().language },
    private val timeoutMillis: Long = INVOKE_TIMEOUT_MILLIS,
    /** Diagnostic messages (action ids and outcomes only); the app routes them to logcat. */
    private val log: (String) -> Unit = {},
    /** Remembers every catalog, so layouts keep titles and icons while no computer is connected. */
    private val cache: ActionCatalogCache? = null,
) : DeckActions {
    private val currentActions = MutableStateFlow<List<DeckAction>>(emptyList())
    private val currentStates = MutableStateFlow<Map<String, DeckActionState>>(emptyMap())
    private val pending = ConcurrentHashMap<String, CompletableDeferred<InvokeResult>>()

    @Volatile
    private var connected = false

    override val actions: StateFlow<List<DeckAction>> get() = currentActions
    override val states: StateFlow<Map<String, DeckActionState>> get() = currentStates

    init {
        scope.launch { transport.incoming.collect(::handle) }
        scope.launch {
            status.state
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { isConnected ->
                    connected = isConnected
                    log("connection ${if (isConnected) "up, asking for the catalog" else "down, clearing actions"}")
                    if (isConnected) {
                        transport.send(Envelope(Protocol.VERSION, Channels.DECK, DeckTypes.CATALOG_GET, id = "catalog"))
                    } else {
                        currentActions.value = emptyList()
                        currentStates.value = emptyMap()
                    }
                }
        }
    }

    override suspend fun invoke(actionId: String): InvokeOutcome {
        if (!connected) {
            log("invoke $actionId: not connected")
            return InvokeOutcome.NotConnected
        }
        val id = UUID.randomUUID().toString()
        val reply = CompletableDeferred<InvokeResult>()
        pending[id] = reply
        try {
            val request = deckMessage(DeckTypes.INVOKE, Invoke(actionId), id = id)
            if (!transport.send(request)) {
                log("invoke $actionId: could not send")
                return InvokeOutcome.NotConnected
            }
            val result = withTimeoutOrNull(timeoutMillis) { reply.await() }
            if (result == null) {
                log("invoke $actionId: no answer within ${timeoutMillis / MILLIS} s")
                return InvokeOutcome.Failure("timed out")
            }
            log("invoke $actionId: ${if (result.ok) "ok" else "refused: ${result.error}"}")
            return if (result.ok) InvokeOutcome.Success else InvokeOutcome.Failure(result.error ?: "failed")
        } finally {
            pending.remove(id)
        }
    }

    private fun handle(message: Envelope) {
        if (message.channel != Channels.DECK) return
        // A message the phone cannot read is ignored: it must not break the connection.
        runCatching {
            log("received deck/${message.type}")
            when (message.type) {
                DeckTypes.CATALOG -> {
                    val actions = message.payloadAs<Catalog>().actions.map(::toAction)
                    currentActions.value = actions
                    cache?.let { scope.launch { runCatching { it.update(actions) } } }
                }

                DeckTypes.STATE -> {
                    val update = message.payloadAs<StateUpdate>()
                    currentStates.value = currentStates.value + (update.actionId to toState(update))
                }

                DeckTypes.RESULT -> {
                    message.id?.let { pending[it] }?.complete(message.payloadAs<InvokeResult>())
                }
            }
        }
    }

    private fun toAction(info: io.github.ratmux.sdhelper.core.protocol.DeckActionInfo): DeckAction =
        DeckAction(
            id = info.id,
            title = info.title[language()] ?: info.title["en"] ?: info.title.values.firstOrNull() ?: info.id,
            icon = info.icon,
            toggle = info.toggle,
        )

    private fun toState(update: StateUpdate) =
        DeckActionState(
            available = update.state.available,
            toggled = update.state.toggled,
            label = update.state.label,
            tone = DeckActionTone.entries.firstOrNull { it.name == update.state.tone } ?: DeckActionTone.NORMAL,
        )

    private companion object {
        const val MILLIS = 1000
        const val INVOKE_TIMEOUT_MILLIS = 10_000L
    }
}
