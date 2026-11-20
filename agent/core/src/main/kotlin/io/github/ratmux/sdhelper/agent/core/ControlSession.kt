package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.AuthResponse
import io.github.ratmux.sdhelper.core.protocol.Channels
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.ErrorCodes
import io.github.ratmux.sdhelper.core.protocol.ErrorPayload
import io.github.ratmux.sdhelper.core.protocol.Hello
import io.github.ratmux.sdhelper.core.protocol.HelloReply
import io.github.ratmux.sdhelper.core.protocol.PairAccepted
import io.github.ratmux.sdhelper.core.protocol.PairRejected
import io.github.ratmux.sdhelper.core.protocol.PairRequest
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs

/** Who this agent is; sent in the `hello` reply. */
data class AgentIdentity(
    val agentId: String,
    val agentName: String,
    val agentVersion: String,
)

/** What to send back for one incoming message, and whether the connection must be closed afterwards. */
data class SessionReaction(
    val replies: List<Envelope> = emptyList(),
    val close: Boolean = false,
)

/**
 * The protocol state machine of **one connection**, independent of the network library.
 *
 * hello → (pair.request | auth.response) → authenticated. Until a device is authenticated only `hello`, `ping`,
 * `pair.request` and `auth.response` are processed; everything else is answered with `unauthenticated`.
 * See docs/PROTOCOL.md section 3.
 */
class ControlSession(
    private val identity: AgentIdentity,
    private val pairing: PairingService,
    private val authenticator: ChallengeAuthenticator,
    private val channels: ChannelRegistry,
    private val limiter: RateLimiter = RateLimiter(),
) {
    /** The authenticated or just-paired device, or null while the connection is anonymous. */
    var device: PairedDevice? = null
        private set

    private var greeted = false
    private var challenge: Challenge? = null
    private var violations = 0

    suspend fun onMessage(message: Envelope): SessionReaction {
        val early = precheck(message)
        if (early != null) return early

        if (!greeted) return handleHello(message)

        val current = device
        if (current != null && !authenticator.isPaired(current.deviceId)) {
            device = null
            return fail(message, ErrorCodes.UNAUTHENTICATED, "device was revoked", close = true)
        }
        return if (message.channel == Channels.CONTROL) handleControl(message) else handleChannel(message, current)
    }

    private fun precheck(message: Envelope): SessionReaction? =
        when {
            message.version != Protocol.VERSION -> {
                fail(message, ErrorCodes.UNSUPPORTED_VERSION, "protocol version ${message.version}", close = true)
            }

            !limiter.tryAcquire() -> {
                violation(message, ErrorCodes.RATE_LIMITED, "too many messages")
            }

            else -> {
                null
            }
        }

    private fun handleHello(message: Envelope): SessionReaction {
        if (message.channel != Channels.CONTROL || message.type != ControlTypes.HELLO) {
            return fail(message, ErrorCodes.UNAUTHENTICATED, "hello expected", close = true)
        }
        val hello =
            runCatching { message.payloadAs<Hello>() }.getOrNull()
                ?: return fail(message, ErrorCodes.MALFORMED, "invalid hello", close = true)
        if (Protocol.VERSION !in hello.protocolVersions) {
            return fail(message, ErrorCodes.UNSUPPORTED_VERSION, "supported: ${Protocol.VERSION}", close = true)
        }
        greeted = true
        val issued = authenticator.newChallenge().also { challenge = it }
        return SessionReaction(
            listOf(
                controlMessage(
                    ControlTypes.HELLO,
                    HelloReply(Protocol.VERSION, identity.agentVersion, identity.agentId, identity.agentName),
                    message.id,
                ),
                controlMessage(ControlTypes.AUTH_CHALLENGE, issued.toPayload()),
            ),
        )
    }

    private suspend fun handleControl(message: Envelope): SessionReaction =
        when (message.type) {
            ControlTypes.PING -> SessionReaction(listOf(controlMessage(ControlTypes.PONG, EmptyPayload(), message.id)))
            ControlTypes.PAIR_REQUEST -> handlePairRequest(message)
            ControlTypes.AUTH_RESPONSE -> handleAuthResponse(message)
            else -> violation(message, ErrorCodes.UNSUPPORTED_TYPE, "control/${message.type}")
        }

    private suspend fun handlePairRequest(message: Envelope): SessionReaction {
        if (device != null) return violation(message, ErrorCodes.MALFORMED, "already authenticated")
        val request =
            runCatching { message.payloadAs<PairRequest>() }.getOrNull()
                ?: return fail(message, ErrorCodes.MALFORMED, "invalid pair.request", close = true)
        return when (val result = pairing.handle(request)) {
            is PairingResult.Accepted -> {
                device = result.device
                SessionReaction(
                    listOf(
                        controlMessage(
                            ControlTypes.PAIR_ACCEPTED,
                            PairAccepted(result.device.deviceId, identity.agentId, identity.agentName),
                            message.id,
                        ),
                    ),
                )
            }

            is PairingResult.Rejected -> {
                SessionReaction(
                    listOf(controlMessage(ControlTypes.PAIR_REJECTED, PairRejected(result.reason), message.id)),
                    close = true,
                )
            }
        }
    }

    private fun handleAuthResponse(message: Envelope): SessionReaction {
        val pending = challenge.also { challenge = null } // a challenge answers at most once
        val response = runCatching { message.payloadAs<AuthResponse>() }.getOrNull()
        val verified = if (pending != null && response != null) authenticator.verify(pending, response) else null
        if (verified == null) {
            return fail(message, ErrorCodes.UNAUTHENTICATED, "authentication failed", close = true)
        }
        device = verified
        return SessionReaction(listOf(controlMessage(ControlTypes.AUTH_OK, EmptyPayload(), message.id)))
    }

    private suspend fun handleChannel(
        message: Envelope,
        current: PairedDevice?,
    ): SessionReaction {
        if (current == null) return violation(message, ErrorCodes.UNAUTHENTICATED, "authenticate first")
        val handler =
            channels.find(message.channel)
                ?: return violation(message, ErrorCodes.UNSUPPORTED_TYPE, "channel ${message.channel}")
        return SessionReaction(handler.handle(current, message))
    }

    /** Protocol error that keeps the connection, but too many of them close it. */
    private fun violation(
        message: Envelope,
        code: String,
        text: String,
    ): SessionReaction {
        violations++
        return fail(message, code, text, close = violations >= MAX_VIOLATIONS)
    }

    private fun fail(
        message: Envelope,
        code: String,
        text: String,
        close: Boolean,
    ) = SessionReaction(listOf(controlMessage(ControlTypes.ERROR, ErrorPayload(code, text), message.id)), close)

    @kotlinx.serialization.Serializable
    private class EmptyPayload

    private companion object {
        const val MAX_VIOLATIONS = 5
    }
}
