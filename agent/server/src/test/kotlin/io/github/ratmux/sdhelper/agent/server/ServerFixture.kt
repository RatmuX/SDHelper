package io.github.ratmux.sdhelper.agent.server

import io.github.ratmux.sdhelper.agent.core.AgentIdentity
import io.github.ratmux.sdhelper.agent.core.ChallengeAuthenticator
import io.github.ratmux.sdhelper.agent.core.ChannelRegistry
import io.github.ratmux.sdhelper.agent.core.ControlSession
import io.github.ratmux.sdhelper.agent.core.FileDeviceRegistry
import io.github.ratmux.sdhelper.agent.core.FileSecretStore
import io.github.ratmux.sdhelper.agent.core.PairingApproval
import io.github.ratmux.sdhelper.agent.core.PairingApprovalRequest
import io.github.ratmux.sdhelper.agent.core.PairingService
import io.github.ratmux.sdhelper.core.protocol.AuthChallenge
import io.github.ratmux.sdhelper.core.protocol.AuthResponse
import io.github.ratmux.sdhelper.core.protocol.AuthSigning
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.Hello
import io.github.ratmux.sdhelper.core.protocol.PairRequest
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import io.github.ratmux.sdhelper.core.protocol.tls.FingerprintTrustManager
import io.github.ratmux.sdhelper.core.protocol.tls.TlsClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A real agent (TLS key, registry on disk, pairing, server) on an ephemeral loopback port. */
class ServerFixture {
    private val dir = Files.createTempDirectory("sdhelper-server-test")
    val tls = TlsIdentity.loadOrCreate(dir.resolve("tls.p12"), FileSecretStore(dir.resolve("secrets")))
    val registry = FileDeviceRegistry(dir.resolve("devices.json"))
    val approvals = mutableListOf<PairingApprovalRequest>()
    var approve = true
    val channels = ChannelRegistry()

    private val pairing =
        PairingService(
            registry,
            tls.fingerprint,
            PairingApproval {
                approvals += it
                approve
            },
        )
    private val authenticator = ChallengeAuthenticator(registry, tls.fingerprint)
    private val identity = AgentIdentity("agent-test", "Test PC", "0.0.0-test")
    val server =
        AgentServer(
            tls,
            { sink -> ControlSession(identity, pairing, authenticator, channels, sink = sink) },
            port = 0,
            bindHost = "127.0.0.1",
        )
    val port: Int = server.start()

    fun newOffer() = pairing.newOffer()

    fun stop() = server.stop()
}

/** The phone side of the protocol, talking over a real TLS WebSocket. */
class WsClient private constructor(
    private val socket: WebSocket,
    private val inbox: LinkedBlockingQueue<Any>,
) {
    private class Closed(
        val code: Int,
    )

    fun send(envelope: Envelope) = sendRaw(Protocol.encode(envelope))

    fun sendRaw(text: String) {
        socket.sendText(text, true).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun next(): Envelope {
        val item = inbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: error("Timed out waiting for a message")
        return item as? Envelope ?: error("Connection closed (${(item as Closed).code}) while waiting for a message")
    }

    /** Waits until the server closed the connection and returns the close code. */
    fun awaitClose(): Int {
        while (true) {
            val item = inbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: error("Connection was not closed")
            if (item is Closed) return item.code
        }
    }

    fun close() {
        runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(1, TimeUnit.SECONDS) }
        socket.abort()
    }

    companion object {
        private const val TIMEOUT_SECONDS = 5L

        fun connect(
            port: Int,
            trustManager: FingerprintTrustManager,
        ): WsClient {
            val inbox = LinkedBlockingQueue<Any>()
            val listener =
                object : WebSocket.Listener {
                    private val buffer = StringBuilder()

                    override fun onText(
                        webSocket: WebSocket,
                        data: CharSequence,
                        last: Boolean,
                    ): CompletionStage<*>? {
                        buffer.append(data)
                        if (last) {
                            inbox.add(Protocol.decode(buffer.toString()))
                            buffer.setLength(0)
                        }
                        webSocket.request(1)
                        return null
                    }

                    override fun onClose(
                        webSocket: WebSocket,
                        statusCode: Int,
                        reason: String,
                    ): CompletionStage<*>? {
                        inbox.add(Closed(statusCode))
                        return null
                    }

                    override fun onError(
                        webSocket: WebSocket,
                        error: Throwable,
                    ) {
                        inbox.add(Closed(-1))
                    }
                }
            val http = HttpClient.newBuilder().sslContext(TlsClient.sslContext(trustManager)).build()
            val socket =
                http
                    .newWebSocketBuilder()
                    .buildAsync(URI("wss://127.0.0.1:$port${AgentServer.PATH}"), listener)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return WsClient(socket, inbox)
        }
    }
}

/** A phone identity: EC P-256 key pair like the Android Keystore one, plus the protocol steps it performs. */
class PhoneIdentity(
    val name: String = "Test Phone",
    private val keyPair: KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair(),
) {
    val publicKey: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    fun hello(): Envelope = controlMessage(ControlTypes.HELLO, Hello(listOf(Protocol.VERSION), "test", name))

    fun pairWithToken(token: String): Envelope =
        controlMessage(ControlTypes.PAIR_REQUEST, PairRequest(name, publicKey, token = token))

    fun pairWithPin(pin: String): Envelope =
        controlMessage(ControlTypes.PAIR_REQUEST, PairRequest(name, publicKey, pin = pin))

    /** Answers the agent's challenge the way the Android app does. */
    fun authResponse(
        deviceId: String,
        challenge: Envelope,
        agentFingerprint: String,
    ): Envelope {
        val nonce = Base64.getUrlDecoder().decode(challenge.payloadAs<AuthChallenge>().nonce)
        val signature = Signature.getInstance(AuthSigning.ALGORITHM)
        signature.initSign(keyPair.private)
        signature.update(AuthSigning.signedBytes(nonce, agentFingerprint))
        return controlMessage(
            ControlTypes.AUTH_RESPONSE,
            AuthResponse(deviceId, Base64.getEncoder().encodeToString(signature.sign())),
        )
    }
}
