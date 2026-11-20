package io.github.ratmux.sdhelper.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingUriTest {
    private val fp = "ab".repeat(32)

    private fun sample() = PairingUri("192.168.1.20", 47821, fp, "tok_en-1", "Murat's PC")

    @Test
    fun `round trip keeps all fields including special characters`() {
        val original = sample()
        assertEquals(original, PairingUri.parse(original.toUriString()))
    }

    @Test
    fun `fingerprint is normalized`() {
        val text = sample().toUriString().replace("fp=$fp", "fp=" + fp.uppercase().chunked(2).joinToString(":"))
        assertEquals(fp, PairingUri.parse(text)?.fingerprint)
    }

    @Test
    fun `malformed or foreign input is rejected`() {
        val valid = sample().toUriString()
        assertNull(PairingUri.parse("https://example.com"))
        assertNull(PairingUri.parse(valid.replace("sdhelper://", "other://")))
        assertNull(PairingUri.parse(valid.replace("v=1", "v=99")))
        assertNull(PairingUri.parse(valid.replace("port=47821", "port=0")))
        assertNull(PairingUri.parse(valid.replace("port=47821", "port=70000")))
        assertNull(PairingUri.parse(valid.replace("fp=$fp", "fp=zz")))
        assertNull(PairingUri.parse(valid.replace("&token=tok_en-1", "")))
        assertNull(PairingUri.parse(""))
    }

    @Test
    fun `short code groups the first eight characters`() {
        assertEquals("ABAB-ABAB", Fingerprint.shortCode(fp))
    }

    @Test
    fun `fingerprint of known input matches sha256`() {
        // SHA-256 of the empty input.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Fingerprint.ofCertificate(ByteArray(0)),
        )
        assertTrue(Fingerprint.isValid(Fingerprint.ofCertificate(byteArrayOf(1, 2, 3))))
        assertNotNull(Fingerprint.normalize(fp))
    }
}
