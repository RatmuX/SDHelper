package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.Hello
import io.github.ratmux.sdhelper.core.protocol.HelloReply
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import io.github.ratmux.sdhelper.core.protocol.tls.FingerprintTrustManager
import io.github.ratmux.sdhelper.core.protocol.tls.TlsClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import okhttp3.Protocol as HttpProtocol

/** A WebSocket speaking protocol envelopes; incoming messages are queued and read with [receive]. */
internal class ProtocolSocket private constructor(
    private val socket: WebSocket,
    private val inbox: Channel<Envelope>,
) {
    fun send(envelope: Envelope) {
        if (!socket.send(Protocol.encode(envelope))) throw IOException("Connection is closed")
    }

    /** Next message; throws when the connection ended (with its cause, if it failed). */
    suspend fun receive(): Envelope = inbox.receive()

    fun close() {
        socket.close(NORMAL_CLOSURE, null)
        socket.cancel()
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000
        private const val INBOX_CAPACITY = 64
        private const val CONNECT_TIMEOUT_SECONDS = 5L

        /**
         * Opens `wss://host:port/v1/ws`, trusting only what [trustManager] accepts. Host names are not verified:
         * the pinned fingerprint is the identity (docs/decisions/ADR-0008).
         */
        suspend fun open(
            host: String,
            port: Int,
            trustManager: FingerprintTrustManager,
        ): ProtocolSocket {
            val client =
                OkHttpClient
                    .Builder()
                    .sslSocketFactory(TlsClient.sslContext(trustManager).socketFactory, trustManager)
                    .hostnameVerifier { _, _ -> true }
                    .protocols(listOf(HttpProtocol.HTTP_1_1))
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build()
            val inbox = Channel<Envelope>(INBOX_CAPACITY)
            val opened = CompletableDeferred<Unit>()
            val listener =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        opened.complete(Unit)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val envelope = runCatching { Protocol.decode(text) }.getOrNull()
                        val delivered = envelope != null && inbox.trySend(envelope).isSuccess
                        if (!delivered) {
                            inbox.close(IOException("Malformed or too many messages"))
                            webSocket.cancel()
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        inbox.close()
                        webSocket.close(NORMAL_CLOSURE, null)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        opened.completeExceptionally(t)
                        inbox.close(t)
                    }
                }
            val url = "wss://${if (':' in host) "[$host]" else host}:$port/v1/ws"
            val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
            try {
                opened.await()
            } catch (e: Throwable) {
                socket.cancel()
                throw e
            }
            return ProtocolSocket(socket, inbox)
        }
    }
}

/** What the agent said in response to `hello`, plus the nonce it wants signed. */
internal class Greeting(
    val reply: HelloReply,
    val challenge: Envelope,
)

private const val HANDSHAKE_TIMEOUT_MILLIS = 10_000L

/** hello → hello reply + auth challenge. Returns null when the agent does not answer in time. */
internal suspend fun ProtocolSocket.greet(deviceName: String): Greeting? {
    send(controlMessage(ControlTypes.HELLO, Hello(listOf(Protocol.VERSION), "0.1.0", deviceName)))
    return withTimeoutOrNull(HANDSHAKE_TIMEOUT_MILLIS) {
        val reply = receive()
        if (reply.type != ControlTypes.HELLO) throw IOException("Unexpected message ${reply.type}")
        val challenge = receive()
        if (challenge.type != ControlTypes.AUTH_CHALLENGE) throw IOException("Unexpected message ${challenge.type}")
        Greeting(reply.payloadAs(), challenge)
    }
}

/** True when the failure was caused by the certificate pin, not by the network. */
internal fun isCertificateProblem(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { it is CertificateException }
