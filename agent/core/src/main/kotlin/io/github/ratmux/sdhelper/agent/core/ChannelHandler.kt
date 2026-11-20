package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.Envelope
import java.util.concurrent.ConcurrentHashMap

/** Sends a message to one connected phone without blocking; returns false when the connection cannot take it. */
fun interface SessionSink {
    fun trySend(message: Envelope): Boolean
}

/** One authenticated connection, as channel handlers see it. Compared by identity. */
class SessionHandle(
    val device: PairedDevice,
    val sink: SessionSink,
)

/**
 * Handles all messages of one protocol channel for authenticated devices.
 * `deck` is registered by the agent; the keyboard (v2) and touchpad (v3) surfaces add their own handler
 * here without touching the session or transport code (docs/ARCHITECTURE.md, "Surfaces").
 */
interface ChannelHandler {
    val channel: String

    suspend fun handle(
        device: PairedDevice,
        message: Envelope,
    ): List<Envelope>

    /** An authenticated connection started; [session] can be used to push messages to it later. */
    fun onSessionStarted(session: SessionHandle) = Unit

    /** The connection ended; forget [session]. */
    fun onSessionEnded(session: SessionHandle) = Unit
}

class ChannelRegistry {
    private val handlers = ConcurrentHashMap<String, ChannelHandler>()

    fun register(handler: ChannelHandler) {
        check(handlers.putIfAbsent(handler.channel, handler) == null) {
            "A handler for channel '${handler.channel}' is already registered"
        }
    }

    fun find(channel: String): ChannelHandler? = handlers[channel]

    fun all(): List<ChannelHandler> = handlers.values.toList()
}
