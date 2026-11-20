package io.github.ratmux.sdhelper.core.protocol

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Content of the pairing QR code: `sdhelper://pair?v=1&host=..&port=..&fp=..&token=..&name=..`.
 * The fingerprint lets the phone trust exactly one certificate; the token proves the phone saw the agent screen.
 */
data class PairingUri(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val token: String,
    val agentName: String,
) {
    fun toUriString(): String =
        buildString {
            append(SCHEME).append("://").append(HOST_PAIR).append("?")
            append("v=").append(Protocol.VERSION)
            append("&host=").append(encode(host))
            append("&port=").append(port)
            append("&fp=").append(fingerprint)
            append("&token=").append(encode(token))
            append("&name=").append(encode(agentName))
        }

    companion object {
        const val SCHEME = "sdhelper"
        private const val HOST_PAIR = "pair"
        private const val MAX_PORT = 65535

        /** Returns null for anything that is not a well-formed pairing URI of a supported version. */
        fun parse(text: String): PairingUri? {
            val prefix = "$SCHEME://$HOST_PAIR?"
            if (!text.startsWith(prefix)) return null
            val query =
                text
                    .removePrefix(prefix)
                    .split("&")
                    .mapNotNull { part ->
                        val index = part.indexOf('=')
                        if (index <= 0) null else part.substring(0, index) to decode(part.substring(index + 1))
                    }.toMap()

            if (query["v"]?.toIntOrNull() != Protocol.VERSION) return null
            val host = query["host"]?.takeIf { it.isNotBlank() } ?: return null
            val port = query["port"]?.toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: return null
            val fingerprint = query["fp"]?.let(Fingerprint::normalize) ?: return null
            val token = query["token"]?.takeIf { it.isNotBlank() } ?: return null
            return PairingUri(host, port, fingerprint, token, query["name"].orEmpty())
        }

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

        private fun decode(value: String) = URLDecoder.decode(value, Charsets.UTF_8)
    }
}
