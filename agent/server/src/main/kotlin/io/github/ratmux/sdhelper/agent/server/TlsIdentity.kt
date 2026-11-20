package io.github.ratmux.sdhelper.agent.server

import io.github.ratmux.sdhelper.agent.core.SecretStore
import io.github.ratmux.sdhelper.core.protocol.Fingerprint
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/** The agent's TLS key and self-signed certificate. Its fingerprint is what phones pin. */
class TlsIdentity(
    val keyStore: KeyStore,
    val alias: String,
    val password: CharArray,
    val fingerprint: String,
) {
    companion object {
        private const val ALIAS = "sdhelper-agent"
        private const val SECRET_NAME = "tls-keystore"
        private const val CERT_DAYS = 3650L
        private const val EC_KEY_BITS = 256

        /**
         * Loads the key store from [file], creating a new ECDSA P-256 certificate on first start.
         * The key store password lives in the [SecretStore].
         */
        fun loadOrCreate(
            file: Path,
            secrets: SecretStore,
        ): TlsIdentity {
            val password = secrets.getOrCreate(SECRET_NAME).toCharArray()
            if (!Files.exists(file)) create(file, password)
            val keyStore = KeyStore.getInstance(file.toFile(), password)
            val certificate = keyStore.getCertificate(ALIAS) as X509Certificate
            return TlsIdentity(keyStore, ALIAS, password, Fingerprint.ofCertificate(certificate.encoded))
        }

        private fun create(
            file: Path,
            password: CharArray,
        ) {
            file.parent?.let { Files.createDirectories(it) }
            val secret = String(password)
            val keyStore =
                buildKeyStore {
                    certificate(ALIAS) {
                        hash = HashAlgorithm.SHA256
                        sign = SignatureAlgorithm.ECDSA
                        keySizeInBits = EC_KEY_BITS
                        this.password = secret
                        daysValid = CERT_DAYS
                        subject = X500Principal("CN=SDHelper Agent")
                    }
                }
            keyStore.saveToFile(file.toFile(), secret)
            runCatching {
                Files.setPosixFilePermissions(
                    file,
                    java.nio.file.attribute.PosixFilePermissions
                        .fromString("rw-------"),
                )
            }
        }
    }
}
