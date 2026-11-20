package io.github.ratmux.sdhelper.agent.platform.windows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs the real `sdhelper-native` executable. Skipped unless SDHELPER_NATIVE_PATH points to it (CI builds it first).
 * On Windows this exercises the real Windows APIs; elsewhere the helper answers "unsupported" and the test still
 * proves process start, stdio framing and shutdown.
 */
class RealHelperTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private fun helper(): NativeHelper {
        val path = System.getenv(NativeHelperLocator.ENV)
        assumeTrue("${NativeHelperLocator.ENV} is not set", path != null && Files.isRegularFile(Paths.get(path)))
        return NativeHelper(ExecutableLauncher(Paths.get(path!!)), scope, timeoutMillis = 15_000)
    }

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(60_000) { block() } }

    @Test
    fun `the helper starts, answers over stdio and stops with the agent`() =
        run {
            val helper = helper()
            val version = helper.call("system.version").jsonObject
            assertTrue(
                version
                    .getValue("version")
                    .jsonPrimitive.content
                    .isNotBlank(),
            )
            // Rust reports "windows", "linux" or "macos"; Java reports "Windows 11", "Linux", "Mac OS X".
            val javaOs = System.getProperty("os.name").orEmpty().lowercase()
            val expected = listOf("windows", "linux").firstOrNull { javaOs.startsWith(it) } ?: "macos"
            assertEquals(expected, version.getValue("os").jsonPrimitive.content)
            helper.close()
        }

    @Test
    fun `a malformed request is answered with an error and the helper keeps running`() =
        run {
            val helper = helper()
            val error = assertFailsWith<RpcException> { helper.call("audio.setVolume") } // neither delta nor value
            assertTrue(error.code == -32602 || error.code == RpcException.UNSUPPORTED)
            assertTrue(helper.call("system.version").jsonObject.containsKey("version"), "still answering")
            helper.close()
        }

    @Test
    fun `secrets are protected with the real windows data protection api`() =
        run {
            assumeTrue("DPAPI exists only on Windows", isWindows)
            val helper = helper()
            val directory = Files.createTempDirectory("sdhelper-real-dpapi")
            val secret = DpapiSecretStore(helper, directory).getOrCreate("tls-keystore")
            assertEquals(secret, DpapiSecretStore(helper, directory).getOrCreate("tls-keystore"))
            assertTrue(!Files.readString(directory.resolve("tls-keystore.secret.dpapi")).contains(secret))
            helper.close()
        }

    @Test
    fun `audio and media queries work, with or without devices`() =
        run {
            assumeTrue("Core Audio and SMTC exist only on Windows", isWindows)
            val helper = helper()
            // A CI machine may have no audio device and no player; both must come back as normal answers.
            val audio = helper.call("audio.get").jsonObject
            assertTrue("output" in audio && "input" in audio)
            val media = helper.call("media.get").jsonObject
            assertTrue("available" in media)
            helper.close()
        }
}
