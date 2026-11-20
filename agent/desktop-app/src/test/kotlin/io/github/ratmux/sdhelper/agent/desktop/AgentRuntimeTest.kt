package io.github.ratmux.sdhelper.agent.desktop

import io.github.ratmux.sdhelper.core.protocol.PairingUri
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentRuntimeTest {
    @Test
    fun `runtime starts, keeps its identity across restarts and builds a valid pairing uri`() {
        val dir = Files.createTempDirectory("sdhelper-runtime-test")

        val first = AgentRuntime({ true }, dir, port = 0, bindHost = "127.0.0.1", announce = false)
        first.start()
        val offer = first.pairing.newOffer()
        val parsed = assertNotNull(PairingUri.parse(first.pairingUri("192.168.1.2", offer).toUriString()))
        assertEquals(first.tls.fingerprint, parsed.fingerprint)
        assertEquals(first.boundPort, parsed.port)
        assertTrue(first.boundPort > 0)
        first.stop()

        val second = AgentRuntime({ true }, dir, port = 0, bindHost = "127.0.0.1", announce = false)
        assertEquals(first.identity.agentId, second.identity.agentId)
        assertEquals(first.tls.fingerprint, second.tls.fingerprint) // phones keep trusting the same certificate
    }
}
