package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.FailureReason
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.protocol.AuthChallenge
import io.github.ratmux.sdhelper.core.protocol.AuthResponse
import io.github.ratmux.sdhelper.core.protocol.AuthSigning
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.ErrorCodes
import io.github.ratmux.sdhelper.core.protocol.ErrorPayload
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import io.github.ratmux.sdhelper.core.protocol.tls.PinnedTrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.util.Base64
import kotlin.random.Random

/** Delay before the next connection attempt: doubles from [initialMillis] up to [maxMillis], with jitter. */
class Backoff(
    private val initialMillis: Long = 1_000,
    private val maxMillis: Long = 30_000,
    private val random: Random = Random.Default,
) {
    fun delayMillis(attempt: Int): Long {
        val doubled = initialMillis shl (attempt - 1).coerceIn(0, MAX_SHIFT)
        val base = doubled.coerceAtMost(maxMillis)
        return (base * (1.0 + (random.nextDouble() - 0.5) * JITTER)).toLong()
    }

    private companion object {
        const val MAX_SHIFT = 20
        const val JITTER = 0.4
    }
}

/**
 * Keeps one authenticated connection to one computer (docs/PROTOCOL.md sections 3 and 5): connects with the pinned
 * certificate, authenticates with the Keystore key, sends a heartbeat, and reconnects with backoff.
 * A wrong certificate or a revoked phone stops retrying: those are not network problems.
 */
class DefaultConnectionManager(
    private val keys: DeviceKeyStore,
    private val deviceName: () -> String,
    private val scope: CoroutineScope,
    private val backoff: Backoff = Backoff(),
    private val pingIntervalMillis: Long = PING_INTERVAL_MILLIS,
    private val silenceTimeoutMillis: Long = SILENCE_TIMEOUT_MILLIS,
) : ConnectionStatus {
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> get() = mutableState

    private var job: Job? = null

    @Synchronized
    fun connect(computer: PairedComputer) {
        job?.cancel()
        job = scope.launch { keepConnected(computer) }
    }

    @Synchronized
    fun disconnect() {
        job?.cancel()
        job = null
        mutableState.value = ConnectionState.Disconnected
    }

    private sealed interface SessionResult {
        /** The connection ended or could not be made; try again. */
        data class Retry(
            val wasConnected: Boolean,
        ) : SessionResult

        data class Stop(
            val reason: FailureReason,
        ) : SessionResult
    }

    private suspend fun keepConnected(computer: PairedComputer) {
        var attempt = 0
        while (true) { // cancellation ends the loop at the next suspension point
            attempt++
            mutableState.value = ConnectionState.Connecting(attempt)
            when (val result = runSession(computer)) {
                is SessionResult.Stop -> {
                    mutableState.value = ConnectionState.Failed(result.reason)
                    return
                }

                is SessionResult.Retry -> {
                    if (result.wasConnected) attempt = 0
                    mutableState.value = ConnectionState.Connecting(attempt + 1)
                    delay(backoff.delayMillis(attempt.coerceAtLeast(1)))
                }
            }
        }
    }

    private suspend fun runSession(computer: PairedComputer): SessionResult {
        var connected = false
        var socket: ProtocolSocket? = null
        try {
            socket = ProtocolSocket.open(computer.host, computer.port, PinnedTrustManager(computer.fingerprint))
            mutableState.value = ConnectionState.Authenticating
            val greeting = socket.greet(deviceName()) ?: return SessionResult.Retry(false)
            if (greeting.reply.agentId != computer.agentId) return SessionResult.Stop(FailureReason.PROTOCOL)

            socket.send(authResponse(computer, greeting.challenge.payloadAs<AuthChallenge>()))
            val answer =
                withTimeoutOrNull(AUTH_TIMEOUT_MILLIS) { socket.receive() } ?: return SessionResult.Retry(false)
            if (answer.type != ControlTypes.AUTH_OK) return authFailure(answer)

            connected = true
            mutableState.value = ConnectionState.Connected(computer.name)
            heartbeat(socket)
            return SessionResult.Retry(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (isCertificateProblem(
                    e,
                )
            ) {
                SessionResult.Stop(FailureReason.CERTIFICATE_MISMATCH)
            } else {
                SessionResult.Retry(connected)
            }
        } finally {
            socket?.close()
        }
    }

    /** The agent refused the signature: it no longer knows this phone. Anything else is a protocol problem. */
    private fun authFailure(answer: Envelope): SessionResult.Stop {
        val unknownDevice =
            answer.type == ControlTypes.ERROR && answer.payloadAs<ErrorPayload>().code == ErrorCodes.UNAUTHENTICATED
        return SessionResult.Stop(if (unknownDevice) FailureReason.REVOKED else FailureReason.PROTOCOL)
    }

    private fun authResponse(
        computer: PairedComputer,
        challenge: AuthChallenge,
    ) = controlMessage(
        ControlTypes.AUTH_RESPONSE,
        AuthResponse(
            deviceId = computer.deviceId,
            signature =
                Base64.getEncoder().encodeToString(
                    keys.sign(
                        computer.keyAlias,
                        AuthSigning.signedBytes(Base64.getUrlDecoder().decode(challenge.nonce), computer.fingerprint),
                    ),
                ),
        ),
    )

    /** Sends pings and returns when the connection is lost or the agent goes silent. */
    private suspend fun heartbeat(socket: ProtocolSocket) =
        coroutineScope {
            val pinger =
                launch {
                    while (true) {
                        socket.send(controlMessage(ControlTypes.PING, EmptyPayload()))
                        delay(pingIntervalMillis)
                    }
                }
            try {
                while (true) {
                    // Any message counts as a sign of life; a closed connection throws and ends the session.
                    if (withTimeoutOrNull(silenceTimeoutMillis) { socket.receive() } == null) break
                }
            } finally {
                pinger.cancel()
            }
        }

    @Serializable
    private class EmptyPayload

    companion object {
        const val PING_INTERVAL_MILLIS = 5_000L
        const val SILENCE_TIMEOUT_MILLIS = 15_000L
        private const val AUTH_TIMEOUT_MILLIS = 10_000L
    }
}
