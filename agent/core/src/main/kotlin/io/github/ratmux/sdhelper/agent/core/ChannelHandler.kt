package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.Envelope
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles all messages of one protocol channel for authenticated devices.
 * `deck` is registered in Phase 3b; the keyboard (v2) and touchpad (v3) surfaces add their own handler
 * here without touching the session or transport code (docs/ARCHITECTURE.md, "Surfaces").
 */
interface ChannelHandler {
    val channel: String

    suspend fun handle(
        device: PairedDevice,
        message: Envelope,
    ): List<Envelope>
}

class ChannelRegistry {
    private val handlers = ConcurrentHashMap<String, ChannelHandler>()

    fun register(handler: ChannelHandler) {
        check(handlers.putIfAbsent(handler.channel, handler) == null) {
            "A handler for channel '${handler.channel}' is already registered"
        }
    }

    fun find(channel: String): ChannelHandler? = handlers[channel]
}
