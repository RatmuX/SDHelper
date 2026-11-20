package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.Fingerprint
import io.github.ratmux.sdhelper.core.protocol.PairRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.Base64
import java.util.UUID

/** Credentials shown on the agent screen: the token goes into the QR code, the PIN is typed by hand. */
data class PairingOffer(
    val token: String,
    val pin: String,
    val expiresAtMillis: Long,
)

/** Asks the person at the PC to confirm a PIN-based pairing, showing a code to compare with the phone. */
fun interface PairingApproval {
    suspend fun approve(request: PairingApprovalRequest): Boolean
}

data class PairingApprovalRequest(
    val deviceName: String,
    val shortCode: String,
)

sealed interface PairingResult {
    data class Accepted(
        val device: PairedDevice,
    ) : PairingResult

    data class Rejected(
        val reason: String,
    ) : PairingResult
}

object PairingRejections {
    const val NO_ACTIVE_OFFER = "no_active_offer"
    const val EXPIRED = "expired"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val LOCKED = "locked"
    const val DENIED = "denied"
    const val INVALID_KEY = "invalid_key"
}

/**
 * Pairing rules (docs/SECURITY.md): one offer at a time, short lifetime, single use, limited attempts.
 * QR flow: the token is the proof. PIN flow: the PIN is the proof plus explicit approval at the PC.
 */
class PairingService(
    private val registry: DeviceRegistry,
    private val agentFingerprint: String,
    private val approval: PairingApproval,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val offerLifetimeMillis: Long = OFFER_LIFETIME_MILLIS,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    private class OfferState(
        val offer: PairingOffer,
        var attempts: Int = 0,
    )

    private val mutex = Mutex()
    private val activeOffer = MutableStateFlow<PairingOffer?>(null)
    private var state: OfferState? = null

    /** The offer currently shown to the user, or null; expiry is checked when a request arrives. */
    val offer: StateFlow<PairingOffer?> get() = activeOffer

    /** Starts a fresh offer, invalidating any previous one. */
    fun newOffer(): PairingOffer {
        val created =
            PairingOffer(
                token = randomToken(),
                pin = "%06d".format(random.nextInt(PIN_SPACE)),
                expiresAtMillis = clock.millis() + offerLifetimeMillis,
            )
        synchronized(this) { state = OfferState(created) }
        activeOffer.value = created
        return created
    }

    fun cancelOffer() {
        synchronized(this) { state = null }
        activeOffer.value = null
    }

    suspend fun handle(request: PairRequest): PairingResult =
        mutex.withLock {
            val current = synchronized(this) { state } ?: return@withLock reject(PairingRejections.NO_ACTIVE_OFFER)
            if (clock.millis() > current.offer.expiresAtMillis) {
                cancelOffer()
                return@withLock reject(PairingRejections.EXPIRED)
            }
            val key = parsePublicKey(request.publicKey) ?: return@withLock reject(PairingRejections.INVALID_KEY)

            val viaToken = request.token != null
            val credentialOk =
                when {
                    request.token != null -> constantTimeEquals(request.token!!, current.offer.token)
                    request.pin != null -> constantTimeEquals(request.pin!!, current.offer.pin)
                    else -> false
                }
            if (!credentialOk) {
                current.attempts++
                if (current.attempts >= maxAttempts) {
                    cancelOffer()
                    return@withLock reject(PairingRejections.LOCKED)
                }
                return@withLock reject(PairingRejections.INVALID_CREDENTIALS)
            }

            val name = sanitizeName(request.deviceName)
            if (!viaToken) {
                val approved = approval.approve(PairingApprovalRequest(name, Fingerprint.shortCode(agentFingerprint)))
                if (!approved) {
                    cancelOffer()
                    return@withLock reject(PairingRejections.DENIED)
                }
            }

            cancelOffer() // single use
            val device = PairedDevice(UUID.randomUUID().toString(), name, key, clock.millis())
            registry.add(device)
            PairingResult.Accepted(device)
        }

    private fun reject(reason: String) = PairingResult.Rejected(reason)

    private fun randomToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Returns the key as stored (normalized Base64) if it is a valid EC public key, else null. */
    private fun parsePublicKey(base64: String): String? =
        runCatching {
            val der = Base64.getDecoder().decode(base64)
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
            base64
        }.getOrNull()

    private fun sanitizeName(raw: String): String =
        raw
            .filter { !it.isISOControl() }
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifEmpty { "Phone" }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ) = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    companion object {
        const val OFFER_LIFETIME_MILLIS = 120_000L
        const val MAX_ATTEMPTS = 5
        private const val PIN_SPACE = 1_000_000
        private const val TOKEN_BYTES = 24
        private const val MAX_NAME_LENGTH = 64
    }
}
