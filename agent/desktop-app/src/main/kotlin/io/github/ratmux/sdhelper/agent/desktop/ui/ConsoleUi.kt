package io.github.ratmux.sdhelper.agent.desktop.ui

import io.github.ratmux.sdhelper.agent.core.PairingApproval
import io.github.ratmux.sdhelper.agent.core.PairingApprovalRequest
import io.github.ratmux.sdhelper.agent.desktop.AgentRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Terminal interface for headless machines and quick tests: prints the pairing QR code and PIN,
 * refreshes them when they expire or get used, and asks for approval on stdin.
 */
class ConsoleUi : PairingApproval {
    override suspend fun approve(request: PairingApprovalRequest): Boolean {
        println()
        println("Pairing request from \"${request.deviceName}\". Code on the phone must read ${request.shortCode}.")
        print("Approve? [y/N] ")
        val answer = withContext(Dispatchers.IO) { readlnOrNull() }
        return answer?.trim()?.lowercase() in setOf("y", "yes")
    }

    /** Never returns: keeps showing fresh pairing offers until the process is stopped. */
    suspend fun run(runtime: AgentRuntime) {
        val address = runtime.addresses().firstOrNull() ?: "127.0.0.1"
        println("${runtime.identity.agentName} listening on port ${runtime.boundPort}")
        println("Certificate fingerprint: ${runtime.tls.fingerprint}")
        println("Paired devices: ${runtime.registry.devices.value.joinToString { it.name }.ifEmpty { "none" }}")
        while (true) {
            val offer = runtime.pairing.newOffer()
            val uri = runtime.pairingUri(address, offer).toUriString()
            println()
            println(Qr.ascii(uri))
            println("Scan with the SDHelper app, or enter address $address with PIN ${offer.pin}")
            println("Valid for ${(offer.expiresAtMillis - System.currentTimeMillis()) / MILLIS} seconds")
            while (runtime.pairing.offer.value == offer && System.currentTimeMillis() < offer.expiresAtMillis) {
                delay(POLL_MILLIS)
            }
        }
    }

    private companion object {
        const val MILLIS = 1000
        const val POLL_MILLIS = 500L
    }
}
