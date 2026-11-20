package io.github.ratmux.sdhelper.core.model

/**
 * A computer this phone is paired with.
 * [fingerprint] pins the agent's TLS certificate; [keyAlias] names the Keystore key registered with that agent.
 */
data class PairedComputer(
    val agentId: String,
    val name: String,
    val host: String,
    val port: Int,
    val fingerprint: String,
    val deviceId: String,
    val keyAlias: String,
)

/** A computer announced on the local network; it may or may not be paired yet. */
data class DiscoveredComputer(
    val agentId: String,
    val name: String,
    val host: String,
    val port: Int,
)
