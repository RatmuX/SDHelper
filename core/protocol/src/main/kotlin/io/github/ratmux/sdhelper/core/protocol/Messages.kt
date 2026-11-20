package io.github.ratmux.sdhelper.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Message types of the `control` channel, see docs/PROTOCOL.md. */
object ControlTypes {
    const val HELLO = "hello"
    const val PAIR_REQUEST = "pair.request"
    const val PAIR_ACCEPTED = "pair.accepted"
    const val PAIR_REJECTED = "pair.rejected"
    const val AUTH_CHALLENGE = "auth.challenge"
    const val AUTH_RESPONSE = "auth.response"
    const val AUTH_OK = "auth.ok"
    const val PING = "ping"
    const val PONG = "pong"
    const val ERROR = "error"
}

/** Error codes carried in [ErrorPayload]. */
object ErrorCodes {
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val UNSUPPORTED_TYPE = "unsupported_type"
    const val UNAUTHENTICATED = "unauthenticated"
    const val RATE_LIMITED = "rate_limited"
    const val MALFORMED = "malformed"
}

/** Client greeting: the first message on every connection. */
@Serializable
data class Hello(
    val protocolVersions: List<Int>,
    val appVersion: String,
    val deviceName: String,
    val capabilities: List<String> = emptyList(),
)

/** Agent answer to [Hello]; also announces the transports it offers (reserved for low-latency input). */
@Serializable
data class HelloReply(
    val protocolVersion: Int,
    val agentVersion: String,
    val agentId: String,
    val agentName: String,
    val transports: List<String> = listOf("ws"),
)

/** Pairing request; exactly one of [token] (QR flow) or [pin] (manual flow) is set. */
@Serializable
data class PairRequest(
    val deviceName: String,
    /** EC P-256 public key, X.509 SubjectPublicKeyInfo, standard Base64. */
    val publicKey: String,
    val token: String? = null,
    val pin: String? = null,
)

@Serializable
data class PairAccepted(
    val deviceId: String,
    val agentId: String,
    val agentName: String,
)

@Serializable
data class PairRejected(
    val reason: String,
)

@Serializable
data class AuthChallenge(
    /** Random nonce, Base64 (URL-safe, no padding). */
    val nonce: String,
)

@Serializable
data class AuthResponse(
    val deviceId: String,
    /** DER-encoded ECDSA signature over [AuthSigning.signedBytes], standard Base64. */
    val signature: String,
)

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String = "",
)

/** Bytes both sides sign and verify during authentication. Bound to the agent's certificate fingerprint. */
object AuthSigning {
    const val ALGORITHM = "SHA256withECDSA"
    private const val DOMAIN = "SDHelper-auth-v1"

    fun signedBytes(
        nonce: ByteArray,
        certificateFingerprint: String,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(DOMAIN.toByteArray(Charsets.UTF_8))
        out.write(0)
        out.write(nonce)
        out.write(MessageDigest.getInstance("SHA-256").digest(certificateFingerprint.toByteArray(Charsets.UTF_8)))
        return out.toByteArray()
    }
}

inline fun <reified T> controlMessage(
    type: String,
    payload: T,
    id: String? = null,
): Envelope = message(Channels.CONTROL, type, payload, id)

/** Builds an envelope for any channel from a serializable payload. */
inline fun <reified T> message(
    channel: String,
    type: String,
    payload: T,
    id: String? = null,
): Envelope =
    Envelope(
        version = Protocol.VERSION,
        channel = channel,
        type = type,
        id = id,
        payload = Protocol.json.encodeToJsonElement(payload).jsonObject,
    )

/** Decodes the payload of this message; throws a serialization exception for malformed payloads. */
inline fun <reified T> Envelope.payloadAs(): T = Protocol.json.decodeFromJsonElement(payload)
