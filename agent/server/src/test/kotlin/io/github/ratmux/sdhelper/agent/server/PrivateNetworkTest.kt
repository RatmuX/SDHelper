package io.github.ratmux.sdhelper.agent.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateNetworkTest {
    @Test
    fun `private, link-local, unique-local and loopback addresses are allowed`() {
        listOf("192.168.1.5", "10.0.0.7", "172.16.4.4", "169.254.1.1", "127.0.0.1", "fd12:3456::1", "fe80::1", "::1")
            .forEach { assertTrue(PrivateNetwork.isAllowed(it), it) }
    }

    @Test
    fun `public and malformed addresses are refused`() {
        listOf("8.8.8.8", "1.1.1.1", "172.32.0.1", "2001:4860:4860::8888", "", "not an address")
            .forEach { assertFalse(PrivateNetwork.isAllowed(it), it) }
    }
}
