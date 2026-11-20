package io.github.ratmux.sdhelper.agent.desktop

import io.github.ratmux.sdhelper.agent.core.AgentIdentity
import io.github.ratmux.sdhelper.agent.core.AgentIdentityStore
import io.github.ratmux.sdhelper.agent.core.AgentInfo
import io.github.ratmux.sdhelper.agent.core.AgentPaths
import io.github.ratmux.sdhelper.agent.core.ChallengeAuthenticator
import io.github.ratmux.sdhelper.agent.core.ChannelRegistry
import io.github.ratmux.sdhelper.agent.core.ControlSession
import io.github.ratmux.sdhelper.agent.core.DeviceRegistry
import io.github.ratmux.sdhelper.agent.core.FileDeviceRegistry
import io.github.ratmux.sdhelper.agent.core.FileSecretStore
import io.github.ratmux.sdhelper.agent.core.PairingApproval
import io.github.ratmux.sdhelper.agent.core.PairingOffer
import io.github.ratmux.sdhelper.agent.core.PairingService
import io.github.ratmux.sdhelper.agent.server.AgentServer
import io.github.ratmux.sdhelper.agent.server.MdnsAdvertiser
import io.github.ratmux.sdhelper.agent.server.PrivateNetwork
import io.github.ratmux.sdhelper.agent.server.TlsIdentity
import io.github.ratmux.sdhelper.core.protocol.PairingUri
import java.net.InetAddress
import java.nio.file.Path

/**
 * Wires the agent together: identity, TLS key, device registry, pairing, server and discovery.
 * The user interface ([ui] package) only talks to this class.
 */
class AgentRuntime(
    approval: PairingApproval,
    configDirectory: Path = AgentPaths.configDirectory(),
    private val port: Int = AgentServer.DEFAULT_PORT,
    bindHost: String = "0.0.0.0",
    private val announce: Boolean = true,
) {
    val identity: AgentIdentity =
        AgentIdentityStore.loadOrCreate(configDirectory, computerName(), AgentInfo().version)
    val tls: TlsIdentity =
        TlsIdentity.loadOrCreate(
            configDirectory.resolve("tls.p12"),
            FileSecretStore(configDirectory.resolve("secrets")),
        )
    val registry: DeviceRegistry = FileDeviceRegistry(configDirectory.resolve("devices.json"))
    val pairing = PairingService(registry, tls.fingerprint, approval)

    private val authenticator = ChallengeAuthenticator(registry, tls.fingerprint)
    private val channels = ChannelRegistry() // channel handlers (deck, later keyboard/touchpad) register here
    private val server =
        AgentServer(
            tls = tls,
            newSession = { ControlSession(identity, pairing, authenticator, channels) },
            port = port,
            bindHost = bindHost,
        )
    private var advertiser: MdnsAdvertiser? = null

    /** The port the server listens on; valid after [start]. */
    var boundPort: Int = port
        private set

    fun start() {
        boundPort = server.start()
        if (announce) {
            // JmDNS can take a few seconds to find network interfaces, so it must not block startup.
            Thread({ advertiser = MdnsAdvertiser(identity, boundPort).also { it.start() } }, "mdns-start")
                .apply { isDaemon = true }
                .start()
        }
    }

    fun stop() {
        advertiser?.close()
        server.stop()
    }

    /** IPv4 addresses a phone on the same network could use to reach this computer. */
    fun addresses(): List<String> = PrivateNetwork.localIpv4Addresses().map { it.hostAddress }

    fun pairingUri(
        host: String,
        offer: PairingOffer,
    ): PairingUri = PairingUri(host, boundPort, tls.fingerprint, offer.token, identity.agentName)

    private fun computerName(): String =
        runCatching { InetAddress.getLocalHost().hostName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "localhost" }
            ?: "SDHelper PC"
}
