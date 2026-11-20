package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairingClient
import io.github.ratmux.sdhelper.core.model.PairingFailure
import io.github.ratmux.sdhelper.core.model.PairingOutcome
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.Fingerprint
import io.github.ratmux.sdhelper.core.protocol.PairAccepted
import io.github.ratmux.sdhelper.core.protocol.PairRejected
import io.github.ratmux.sdhelper.core.protocol.PairRequest
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import io.github.ratmux.sdhelper.core.protocol.tls.CapturingTrustManager
import io.github.ratmux.sdhelper.core.protocol.tls.FingerprintTrustManager
import io.github.ratmux.sdhelper.core.protocol.tls.PinnedTrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Phone side of pairing (docs/PROTOCOL.md section 3.3). A fresh Keystore key is created per computer and removed
 * again if pairing does not succeed.
 */
class DefaultPairingClient(
    private val keys: DeviceKeyStore,
    private val deviceName: () -> String,
) : PairingClient {
    override suspend fun pairWithQr(
        host: String,
        port: Int,
        fingerprint: String,
        token: String,
    ): PairingOutcome =
        pair(host, port, PinnedTrustManager(fingerprint), QR_TIMEOUT_MILLIS) { publicKey, _ ->
            PairRequest(deviceName(), publicKey, token = token)
        }

    override suspend fun pairWithPin(
        host: String,
        port: Int,
        pin: String,
        onVerificationCode: (String) -> Unit,
    ): PairingOutcome =
        pair(host, port, CapturingTrustManager(), PIN_TIMEOUT_MILLIS) { publicKey, observed ->
            // The person compares this code with the one on the PC before approving there.
            observed?.let { onVerificationCode(Fingerprint.shortCode(it)) }
            PairRequest(deviceName(), publicKey, pin = pin)
        }

    override fun forget(computer: PairedComputer) = keys.delete(computer.keyAlias)

    private suspend fun pair(
        host: String,
        port: Int,
        trust: FingerprintTrustManager,
        timeoutMillis: Long,
        buildRequest: (publicKey: String, observedFingerprint: String?) -> PairRequest,
    ): PairingOutcome {
        val alias = "sdhelper-" + UUID.randomUUID()
        val publicKey = keys.generate(alias)
        var succeeded = false
        try {
            val outcome = exchange(host, port, trust, timeoutMillis, alias, publicKey, buildRequest)
            succeeded = outcome is PairingOutcome.Success
            return outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PairingOutcome.Failure(
                if (isCertificateProblem(e)) PairingFailure.CERTIFICATE_MISMATCH else PairingFailure.UNREACHABLE,
            )
        } finally {
            if (!succeeded) keys.delete(alias)
        }
    }

    private suspend fun exchange(
        host: String,
        port: Int,
        trust: FingerprintTrustManager,
        timeoutMillis: Long,
        alias: String,
        publicKey: String,
        buildRequest: (String, String?) -> PairRequest,
    ): PairingOutcome {
        val socket = ProtocolSocket.open(host, port, trust)
        try {
            val greeting = socket.greet(deviceName()) ?: return PairingOutcome.Failure(PairingFailure.UNREACHABLE)
            val observed = (trust as? CapturingTrustManager)?.observedFingerprint
            val pinnedFingerprint = observed ?: (trust as PinnedTrustManager).expectedFingerprint
            socket.send(controlMessage(ControlTypes.PAIR_REQUEST, buildRequest(publicKey, observed)))

            val answer =
                withTimeoutOrNull(timeoutMillis) { socket.receive() }
                    ?: return PairingOutcome.Failure(PairingFailure.REJECTED)
            return interpret(answer, greeting, host, port, pinnedFingerprint, alias)
        } finally {
            socket.close()
        }
    }

    private fun interpret(
        answer: Envelope,
        greeting: Greeting,
        host: String,
        port: Int,
        fingerprint: String,
        alias: String,
    ): PairingOutcome =
        when (answer.type) {
            ControlTypes.PAIR_ACCEPTED -> {
                val accepted = answer.payloadAs<PairAccepted>()
                PairingOutcome.Success(
                    PairedComputer(
                        agentId = accepted.agentId,
                        name = greeting.reply.agentName,
                        host = host,
                        port = port,
                        fingerprint = fingerprint,
                        deviceId = accepted.deviceId,
                        keyAlias = alias,
                    ),
                )
            }

            ControlTypes.PAIR_REJECTED -> {
                PairingOutcome.Failure(
                    when (answer.payloadAs<PairRejected>().reason) {
                        "denied" -> PairingFailure.DENIED
                        "locked" -> PairingFailure.LOCKED
                        else -> PairingFailure.REJECTED
                    },
                )
            }

            else -> {
                PairingOutcome.Failure(PairingFailure.PROTOCOL)
            }
        }

    private companion object {
        const val QR_TIMEOUT_MILLIS = 15_000L
        const val PIN_TIMEOUT_MILLIS = 130_000L // the offer lives 2 minutes; the person at the PC may take their time
    }
}
