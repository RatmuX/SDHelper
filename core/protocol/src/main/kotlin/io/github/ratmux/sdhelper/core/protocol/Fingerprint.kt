package io.github.ratmux.sdhelper.core.protocol

import java.security.MessageDigest

/** SHA-256 fingerprint of an agent's TLS certificate, written as 64 lowercase hex characters. */
object Fingerprint {
    private const val HEX_LENGTH = 64

    fun ofCertificate(derEncoded: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(derEncoded))

    fun isValid(value: String): Boolean = value.length == HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }

    /** Accepts upper case and `:` separators, returns the canonical form or null. */
    fun normalize(value: String): String? =
        value
            .lowercase()
            .replace(":", "")
            .takeIf(::isValid)

    /**
     * Short human-comparable code, for example `AB12-CD34`, shown on both screens during PIN pairing.
     * Four characters per group, first two groups of the fingerprint.
     */
    fun shortCode(fingerprint: String): String =
        fingerprint
            .take(8)
            .uppercase()
            .chunked(4)
            .joinToString("-")

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
