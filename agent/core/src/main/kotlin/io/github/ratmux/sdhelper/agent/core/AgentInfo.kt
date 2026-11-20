package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.Protocol

/** Identity of the running agent, sent in the protocol `hello` reply. */
data class AgentInfo(
    val name: String = "SDHelper Agent",
    val version: String = "0.1.0",
    val protocolVersion: Int = Protocol.VERSION,
) {
    fun banner(): String = "$name $version (protocol v$protocolVersion)"
}
