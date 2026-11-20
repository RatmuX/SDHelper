package io.github.ratmux.sdhelper.agent.platform.windows

import io.github.ratmux.sdhelper.agent.core.platform.PlatformException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DpapiSecretStoreTest {
    private val directory = Files.createTempDirectory("sdhelper-dpapi-test").resolve("secrets")

    /** Pretends to be DPAPI: reverses the bytes, which is enough to see that data really goes through it. */
    private val helper =
        FakeHelperRpc { method, params ->
            val bytes = Base64.getDecoder().decode(params.getValue("data").jsonPrimitive.content)
            check(method == "dpapi.protect" || method == "dpapi.unprotect")
            buildJsonObject { put("data", Base64.getEncoder().encodeToString(bytes.reversedArray())) }
        }

    @Test
    fun `a new secret is stored only in protected form and stays stable`() {
        val store = DpapiSecretStore(helper, directory)
        val secret = store.getOrCreate("tls-keystore")

        assertTrue(secret.length >= 32)
        assertEquals(secret, store.getOrCreate("tls-keystore"))
        assertEquals(secret, DpapiSecretStore(helper, directory).getOrCreate("tls-keystore"))
        assertNotEquals(secret, store.getOrCreate("other"))

        assertFalse(Files.exists(directory.resolve("tls-keystore.secret")), "no plain file")
        val stored = Files.readString(directory.resolve("tls-keystore.secret.dpapi"))
        assertFalse(stored.contains(secret))
        assertFalse(String(Base64.getDecoder().decode(stored.trim())).contains(secret))
    }

    @Test
    fun `a plain secret from an earlier version is protected in place and the plain file is removed`() {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("tls-keystore.secret"), "old-plain-secret\n")

        val secret = DpapiSecretStore(helper, directory).getOrCreate("tls-keystore")

        assertEquals("old-plain-secret", secret)
        assertFalse(Files.exists(directory.resolve("tls-keystore.secret")))
        assertEquals("old-plain-secret", DpapiSecretStore(helper, directory).getOrCreate("tls-keystore"))
    }

    @Test
    fun `without the helper there is no silent fallback to plain files`() {
        val broken = FakeHelperRpc { _, _ -> throw HelperClosedException() }
        assertFailsWith<PlatformException> { DpapiSecretStore(broken, directory).getOrCreate("tls-keystore") }
        assertFalse(Files.exists(directory.resolve("tls-keystore.secret")))
        assertFalse(Files.exists(directory.resolve("tls-keystore.secret.dpapi")))
    }

    @Test
    fun `a secret that windows refuses to decrypt is an error, not a new secret`() {
        val first = DpapiSecretStore(helper, directory).getOrCreate("tls-keystore")
        val failing = FakeHelperRpc { _, _ -> throw FakeRpcError(-32000, "bad data") }
        assertFailsWith<PlatformException> { DpapiSecretStore(failing, directory).getOrCreate("tls-keystore") }
        assertEquals(
            first,
            DpapiSecretStore(helper, directory).getOrCreate("tls-keystore"),
            "the stored secret was not replaced",
        )
    }

    @Test
    fun `secret names cannot escape the directory`() {
        assertFailsWith<IllegalArgumentException> { DpapiSecretStore(helper, directory).getOrCreate("../evil") }
    }
}
