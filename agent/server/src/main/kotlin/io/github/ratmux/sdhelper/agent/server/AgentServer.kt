package io.github.ratmux.sdhelper.agent.server

import io.github.ratmux.sdhelper.agent.core.ControlSession
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.ErrorCodes
import io.github.ratmux.sdhelper.core.protocol.ErrorPayload
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.origin
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * TLS WebSocket server of the agent: `wss://<host>:<port>/v1/ws`. It only moves frames; the protocol
 * rules live in the [ControlSession] that [newSession] creates for each connection.
 * Limits (docs/SECURITY.md): private networks only, 64 KiB frames, bounded idle time, bounded connections.
 */
class AgentServer(
    private val tls: TlsIdentity,
    private val newSession: () -> ControlSession,
    private val port: Int = DEFAULT_PORT,
    private val bindHost: String = "0.0.0.0",
    private val maxConnections: Int = MAX_CONNECTIONS,
) {
    private var server: EmbeddedServer<*, *>? = null
    private val connections = AtomicInteger(0)

    /** Starts listening and returns the port actually bound (useful when [port] is 0). */
    fun start(): Int {
        check(server == null) { "Already started" }
        val engine =
            embeddedServer(
                Netty,
                configure = {
                    sslConnector(
                        keyStore = tls.keyStore,
                        keyAlias = tls.alias,
                        keyStorePassword = { tls.password },
                        privateKeyPassword = { tls.password },
                    ) {
                        host = bindHost
                        port = this@AgentServer.port
                    }
                },
            ) {
                install(WebSockets) {
                    maxFrameSize = MAX_FRAME_BYTES
                    pingPeriodMillis = PING_PERIOD_MILLIS
                    timeoutMillis = PING_TIMEOUT_MILLIS
                }
                routing { webSocket(PATH) { serve() } }
            }
        engine.start(wait = false)
        server = engine
        return runBlocking {
            engine.engine
                .resolvedConnectors()
                .first()
                .port
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        server = null
    }

    private suspend fun DefaultWebSocketServerSession.serve() {
        if (!PrivateNetwork.isAllowed(call.request.origin.remoteAddress)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "network not allowed"))
            return
        }
        if (connections.incrementAndGet() > maxConnections) {
            connections.decrementAndGet()
            close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "too many connections"))
            return
        }
        try {
            pump(newSession())
        } finally {
            connections.decrementAndGet()
        }
    }

    private suspend fun DefaultWebSocketServerSession.pump(session: ControlSession) {
        val startedAt = System.nanoTime()
        var badFrames = 0
        while (true) {
            val frame = nextFrame(session, startedAt) ?: break
            val text = (frame as? Frame.Text)?.readText()
            val envelope = text?.let { runCatching { Protocol.decode(it) }.getOrNull() }
            if (envelope == null) {
                send(
                    Frame.Text(Protocol.encode(controlMessage(ControlTypes.ERROR, ErrorPayload(ErrorCodes.MALFORMED)))),
                )
                if (++badFrames >= MAX_BAD_FRAMES) break
                continue
            }
            val reaction = session.onMessage(envelope)
            reaction.replies.forEach { send(Frame.Text(Protocol.encode(it))) }
            if (reaction.close) break
        }
        close(CloseReason(CloseReason.Codes.NORMAL, ""))
    }

    /** Next frame, or null when the peer left, went idle, or an anonymous connection stayed too long. */
    private suspend fun DefaultWebSocketServerSession.nextFrame(
        session: ControlSession,
        startedAt: Long,
    ): Frame? {
        val anonymous = session.device == null
        val budget =
            if (anonymous) {
                val elapsed = (System.nanoTime() - startedAt) / NANOS_PER_SECOND
                minOf(ANONYMOUS_IDLE, ANONYMOUS_LIFETIME - elapsed.seconds)
            } else {
                IDLE
            }
        if (budget <= 0.seconds) return null
        return withTimeoutOrNull(budget) { incoming.receiveCatching().getOrNull() }
    }

    companion object {
        const val DEFAULT_PORT = 47821
        const val PATH = "/v1/ws"
        private const val MAX_FRAME_BYTES = 64L * 1024
        private const val MAX_CONNECTIONS = 16
        private const val MAX_BAD_FRAMES = 5
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val PING_PERIOD_MILLIS = 15_000L
        private const val PING_TIMEOUT_MILLIS = 30_000L
        private val IDLE = 30.seconds
        private val ANONYMOUS_IDLE = 15.seconds
        private val ANONYMOUS_LIFETIME = 60.seconds
    }
}
