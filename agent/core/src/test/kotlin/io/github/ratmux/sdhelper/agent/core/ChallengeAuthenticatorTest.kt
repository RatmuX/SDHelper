package io.github.ratmux.sdhelper.agent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeAuthenticatorTest {
    private val registry = InMemoryDeviceRegistry()
    private val authenticator = ChallengeAuthenticator(registry, TEST_FINGERPRINT)
    private val phone = FakePhone()
    private val device = PairedDevice("dev-1", "Phone", phone.publicKeyBase64, 0)

    init {
        registry.add(device)
    }

    @Test
    fun `a valid signature authenticates the device`() {
        val challenge = authenticator.newChallenge()
        assertEquals(device, authenticator.verify(challenge, phone.authResponse("dev-1", challenge.nonce)))
    }

    @Test
    fun `a signature by another key is rejected`() {
        val challenge = authenticator.newChallenge()
        val attacker = FakePhone()
        assertNull(authenticator.verify(challenge, attacker.authResponse("dev-1", challenge.nonce)))
    }

    @Test
    fun `a signature made for another certificate fingerprint is rejected`() {
        val challenge = authenticator.newChallenge()
        val relayed = phone.authResponse("dev-1", challenge.nonce, certificateFingerprint = "ee".repeat(32))
        assertNull(authenticator.verify(challenge, relayed))
    }

    @Test
    fun `a signature over an old nonce is rejected`() {
        val old = authenticator.newChallenge()
        val fresh = authenticator.newChallenge()
        assertNull(authenticator.verify(fresh, phone.authResponse("dev-1", old.nonce)))
    }

    @Test
    fun `a revoked device can no longer authenticate`() {
        val challenge = authenticator.newChallenge()
        val response = phone.authResponse("dev-1", challenge.nonce)
        assertTrue(authenticator.isPaired("dev-1"))

        registry.revoke("dev-1")

        assertFalse(authenticator.isPaired("dev-1"))
        assertNull(authenticator.verify(challenge, response))
    }

    @Test
    fun `garbage signatures and unknown devices are rejected without exceptions`() {
        val challenge = authenticator.newChallenge()
        assertNull(
            authenticator.verify(challenge, phone.authResponse("dev-1", challenge.nonce).copy(signature = "%%%")),
        )
        assertNull(authenticator.verify(challenge, phone.authResponse("nobody", challenge.nonce)))
    }

    @Test
    fun `challenges are random`() {
        assertFalse(authenticator.newChallenge().nonce.contentEquals(authenticator.newChallenge().nonce))
    }
}
