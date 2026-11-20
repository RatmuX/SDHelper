package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.AuthChallenge
import io.github.ratmux.sdhelper.core.protocol.AuthResponse
import io.github.ratmux.sdhelper.core.protocol.AuthSigning
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** A challenge issued to one connection; the nonce is kept to verify the answer. */
class Challenge(
    val nonce: ByteArray,
) {
    fun toPayload() = AuthChallenge(Base64.getUrlEncoder().withoutPadding().encodeToString(nonce))
}

/**
 * Authenticates a paired phone: it must sign the nonce together with the certificate fingerprint of
 * *this* agent with the private key created at pairing time (docs/SECURITY.md, "Pairing flow").
 */
class ChallengeAuthenticator(
    private val registry: DeviceRegistry,
    private val agentFingerprint: String,
    private val random: SecureRandom = SecureRandom(),
) {
    fun newChallenge(): Challenge = Challenge(ByteArray(NONCE_BYTES).also(random::nextBytes))

    /** False once the device was revoked, even while a session of it is still open. */
    fun isPaired(deviceId: String): Boolean = registry.find(deviceId) != null

    /** Returns the device if the signature is valid for a device that is still paired, otherwise null. */
    fun verify(
        challenge: Challenge,
        response: AuthResponse,
    ): PairedDevice? {
        val device = registry.find(response.deviceId) ?: return null
        return runCatching {
            val publicKey =
                KeyFactory
                    .getInstance("EC")
                    .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(device.publicKey)))
            val signature = Signature.getInstance(AuthSigning.ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(AuthSigning.signedBytes(challenge.nonce, agentFingerprint))
            if (signature.verify(Base64.getDecoder().decode(response.signature))) device else null
        }.getOrNull()
    }

    private companion object {
        const val NONCE_BYTES = 32
    }
}
