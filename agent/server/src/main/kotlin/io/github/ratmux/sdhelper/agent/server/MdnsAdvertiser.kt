package io.github.ratmux.sdhelper.agent.server

import io.github.ratmux.sdhelper.agent.core.AgentIdentity
import io.github.ratmux.sdhelper.core.protocol.Protocol
import java.util.logging.Level
import java.util.logging.Logger
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Announces the agent as `_sdhelper._tcp` on the local network so phones can list it.
 * Only the agent id and protocol version are published; trust still comes from pairing.
 * Discovery is a convenience: if it fails, pairing by QR code or IP address keeps working.
 */
class MdnsAdvertiser(
    private val identity: AgentIdentity,
    private val port: Int,
) : AutoCloseable {
    private var jmdns: JmDNS? = null

    /** Returns false (and logs) when announcing is not possible on this network. */
    fun start(): Boolean =
        runCatching {
            val instance = JmDNS.create()
            val info =
                ServiceInfo.create(
                    SERVICE_TYPE,
                    identity.agentName,
                    port,
                    0,
                    0,
                    mapOf("id" to identity.agentId, "v" to Protocol.VERSION.toString()),
                )
            instance.registerService(info)
            jmdns = instance
        }.onFailure { LOG.log(Level.WARNING, "mDNS announcement failed; use QR code or IP address", it) }
            .isSuccess

    override fun close() {
        runCatching {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        }
        jmdns = null
    }

    companion object {
        const val SERVICE_TYPE = "_sdhelper._tcp.local."
        private val LOG = Logger.getLogger(MdnsAdvertiser::class.java.name)
    }
}
