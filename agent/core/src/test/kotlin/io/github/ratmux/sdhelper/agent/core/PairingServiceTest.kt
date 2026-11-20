package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.Fingerprint
import io.github.ratmux.sdhelper.core.protocol.PairRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingServiceTest {
    private val clock = MutableClock()
    private val registry = InMemoryDeviceRegistry()
    private val approvals = mutableListOf<PairingApprovalRequest>()
    private var approve = true

    private val service =
        PairingService(
            registry = registry,
            agentFingerprint = TEST_FINGERPRINT,
            approval =
                PairingApproval {
                    approvals += it
                    approve
                },
            clock = clock,
        )

    private val phone = FakePhone()

    private fun rejected(result: PairingResult) = assertIs<PairingResult.Rejected>(result).reason

    @Test
    fun `token pairing registers the device without asking the user`() =
        runTest {
            val offer = service.newOffer()
            val result = service.handle(phone.pairRequestWithToken(offer.token))

            val device = assertIs<PairingResult.Accepted>(result).device
            assertEquals(device, registry.find(device.deviceId))
            assertEquals(phone.publicKeyBase64, device.publicKey)
            assertTrue(approvals.isEmpty())
        }

    @Test
    fun `an offer can be used only once`() =
        runTest {
            val offer = service.newOffer()
            assertIs<PairingResult.Accepted>(service.handle(phone.pairRequestWithToken(offer.token)))

            assertEquals(
                PairingRejections.NO_ACTIVE_OFFER,
                rejected(service.handle(phone.pairRequestWithToken(offer.token))),
            )
            assertNull(service.offer.value)
            assertEquals(1, registry.devices.value.size)
        }

    @Test
    fun `a new offer invalidates the previous one`() =
        runTest {
            val first = service.newOffer()
            service.newOffer()
            assertEquals(
                PairingRejections.INVALID_CREDENTIALS,
                rejected(service.handle(phone.pairRequestWithToken(first.token))),
            )
        }

    @Test
    fun `an expired offer is rejected`() =
        runTest {
            val offer = service.newOffer()
            clock.advance(PairingService.OFFER_LIFETIME_MILLIS + 1)

            assertEquals(PairingRejections.EXPIRED, rejected(service.handle(phone.pairRequestWithToken(offer.token))))
            assertTrue(registry.devices.value.isEmpty())
        }

    @Test
    fun `five wrong attempts lock the offer even for the right credential`() =
        runTest {
            val offer = service.newOffer()
            repeat(4) {
                assertEquals(
                    PairingRejections.INVALID_CREDENTIALS,
                    rejected(service.handle(phone.pairRequestWithPin("000000".takeIf { offer.pin != it } ?: "000001"))),
                )
            }
            assertEquals(PairingRejections.LOCKED, rejected(service.handle(phone.pairRequestWithPin("999999x"))))
            assertEquals(
                PairingRejections.NO_ACTIVE_OFFER,
                rejected(service.handle(phone.pairRequestWithToken(offer.token))),
            )
        }

    @Test
    fun `pin pairing needs approval at the pc and shows the certificate code`() =
        runTest {
            val offer = service.newOffer()
            val result = service.handle(phone.pairRequestWithPin(offer.pin))

            assertIs<PairingResult.Accepted>(result)
            assertEquals(listOf(PairingApprovalRequest(phone.name, Fingerprint.shortCode(TEST_FINGERPRINT))), approvals)
        }

    @Test
    fun `a denied pin pairing registers nothing and burns the offer`() =
        runTest {
            approve = false
            val offer = service.newOffer()

            assertEquals(PairingRejections.DENIED, rejected(service.handle(phone.pairRequestWithPin(offer.pin))))
            assertTrue(registry.devices.value.isEmpty())
            assertNull(service.offer.value)
        }

    @Test
    fun `a request with an invalid public key is rejected`() =
        runTest {
            val offer = service.newOffer()
            val bad = PairRequest("Phone", "bm90IGEga2V5", token = offer.token)
            assertEquals(PairingRejections.INVALID_KEY, rejected(service.handle(bad)))
        }

    @Test
    fun `a request without any credential counts as a failed attempt`() =
        runTest {
            service.newOffer()
            val none = PairRequest("Phone", phone.publicKeyBase64)
            assertEquals(PairingRejections.INVALID_CREDENTIALS, rejected(service.handle(none)))
        }

    @Test
    fun `device names are cleaned up`() =
        runTest {
            val offer = service.newOffer()
            val request = PairRequest("  \u0007Pixel\n" + "x".repeat(100), phone.publicKeyBase64, token = offer.token)
            val device = assertIs<PairingResult.Accepted>(service.handle(request)).device
            assertTrue(device.name.startsWith("Pixelx"))
            assertEquals(64, device.name.length)
        }

    @Test
    fun `without an offer nothing can be paired`() =
        runTest {
            assertEquals(PairingRejections.NO_ACTIVE_OFFER, rejected(service.handle(phone.pairRequestWithToken("x"))))
        }
}
