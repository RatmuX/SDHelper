package io.github.ratmux.sdhelper.agent.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentInfoTest {
    @Test
    fun `banner reports name, version and protocol`() {
        assertEquals("SDHelper Agent 0.1.0 (protocol v1)", AgentInfo().banner())
    }
}
