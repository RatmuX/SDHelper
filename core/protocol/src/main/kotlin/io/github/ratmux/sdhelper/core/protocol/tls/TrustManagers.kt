package io.github.ratmux.sdhelper.core.protocol.tls

import io.github.ratmux.sdhelper.core.protocol.Fingerprint
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Base for trust managers that decide on the leaf certificate's fingerprint alone.
 *
 * It extends [X509ExtendedTrustManager] on purpose: for a plain `X509TrustManager` the JSSE adds its own
 * hostname verification, which can never match a self-signed certificate reached by IP address. With the
 * extended variant the trust manager is responsible for the identity check, and ours is the fingerprint pin.
 */
abstract class FingerprintTrustManager : X509ExtendedTrustManager() {
    protected abstract fun checkLeaf(fingerprint: String)

    private fun check(chain: Array<out X509Certificate>?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty certificate chain")
        checkLeaf(Fingerprint.ofCertificate(leaf.encoded))
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = check(chain)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = check(chain)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = check(chain)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ): Unit = throw CertificateException("Client certificates are not used")

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ): Unit = throw CertificateException("Client certificates are not used")

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ): Unit = throw CertificateException("Client certificates are not used")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * Trusts exactly one certificate: the one whose SHA-256 fingerprint was learned out of band (QR code) or
 * stored when the computer was paired. Replaces CA and hostname validation for the self-signed agent
 * certificate (docs/SECURITY.md). Validity dates are deliberately not checked: the pin is the identity.
 */
class PinnedTrustManager(
    expectedFingerprint: String,
) : FingerprintTrustManager() {
    /** The pinned fingerprint in canonical form (64 lowercase hex characters). */
    val expectedFingerprint: String =
        Fingerprint.normalize(expectedFingerprint) ?: throw IllegalArgumentException("Invalid fingerprint")

    private val expectedBytes = this.expectedFingerprint.toByteArray(Charsets.UTF_8)

    override fun checkLeaf(fingerprint: String) {
        if (!MessageDigest.isEqual(expectedBytes, fingerprint.toByteArray(Charsets.UTF_8))) {
            throw CertificateException("Certificate fingerprint does not match the paired computer")
        }
    }
}

/**
 * Accepts any certificate and records its fingerprint. **Only** for the PIN pairing handshake, where the
 * person must compare the short code derived from [observedFingerprint] on the phone and on the PC before
 * approving. Never use it for ordinary connections.
 */
class CapturingTrustManager : FingerprintTrustManager() {
    @Volatile
    var observedFingerprint: String? = null
        private set

    override fun checkLeaf(fingerprint: String) {
        observedFingerprint = fingerprint
    }
}

object TlsClient {
    /** TLS context that uses only the given trust manager. */
    fun sslContext(trustManager: FingerprintTrustManager): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), SecureRandom()) }
}
