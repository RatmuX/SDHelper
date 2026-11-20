package io.github.ratmux.sdhelper.agent.platform.windows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeHelperTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val launched = mutableListOf<FakeHelperProcess>()
    private var launcherFailure: IOException? = null
    private var handler: (
        String,
        JsonObject,
    ) -> kotlinx.serialization.json.JsonElement = { _, _ -> json("""{"ok":true}""") }

    private class MovableClock(
        var millis: Long = 1_000_000,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(millis)
    }

    private val clock = MovableClock()

    private val launcher =
        ProcessLauncher {
            launcherFailure?.let { throw it }
            FakeHelperProcess { method, params -> handler(method, params) }.also { launched += it }
        }

    private fun helper(maxStarts: Int = 5) =
        NativeHelper(launcher, scope, timeoutMillis = 3_000, maxStartsPerMinute = maxStarts, clock = clock)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        launched.forEach { it.destroy() }
    }

    private fun run(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        runBlocking {
            withTimeout(20_000) { block() }
        }

    @Test
    fun `the helper starts on first use and is reused`() =
        run {
            val helper = helper()
            assertEquals(0, launched.size)

            helper.call("a")
            helper.call("b")

            assertEquals(1, launched.size)
            helper.close()
        }

    @Test
    fun `a dead helper is started again on the next call`() =
        run {
            val helper = helper()
            helper.call("a")
            launched.single().crash()
            while (launched.single().isAlive) delay(10)

            helper.call("b")

            assertEquals(2, launched.size)
            assertTrue(launched.last().isAlive)
            helper.close()
        }

    @Test
    fun `a call that was already sent when the helper died is not repeated`() =
        run {
            val release = CountDownLatch(1)
            handler = { _, _ ->
                release.await(5, TimeUnit.SECONDS)
                json("{}")
            }
            val helper = helper()
            val pending = async { runCatching { helper.call("toggle") } }
            while (launched.isEmpty() || launched.single().received.isEmpty()) delay(10)

            launched.single().crash()
            release.countDown()

            assertTrue(pending.await().exceptionOrNull() is HelperClosedException)
            assertEquals(1, launched.size, "no hidden retry")
            assertEquals(listOf("toggle"), launched.single().received.toList())
            helper.close()
        }

    @Test
    fun `restarts are limited per minute so a broken helper cannot spin`() =
        run {
            val helper = helper(maxStarts = 2)
            repeat(2) {
                helper.call("a")
                launched.last().crash()
                while (launched.last().isAlive) delay(10)
            }

            val refusal = assertFailsWith<HelperClosedException> { helper.call("a") }
            assertTrue(refusal.message!!.contains("giving up"))
            assertEquals(2, launched.size)

            clock.millis += 61_000
            helper.call("a")
            assertEquals(3, launched.size)
            helper.close()
        }

    @Test
    fun `a helper that cannot be started is reported as closed`() =
        run {
            launcherFailure = IOException("file not found")
            val error = assertFailsWith<HelperClosedException> { helper().call("a") }
            assertTrue(error.message!!.contains("file not found"))
        }

    @Test
    fun `notifications are relayed and the end of a helper is announced`() =
        run {
            val helper = helper()
            helper.call("a")
            val changed =
                async(start = CoroutineStart.UNDISPATCHED) {
                    helper.notifications.first {
                        it.method ==
                            "audio.changed"
                    }
                }
            val closed =
                async(start = CoroutineStart.UNDISPATCHED) {
                    helper.notifications.first {
                        it.method ==
                            HELPER_CLOSED
                    }
                }

            launched.single().notify("audio.changed")
            assertEquals("audio.changed", changed.await().method)

            launched.single().crash()
            assertEquals(HELPER_CLOSED, closed.await().method)
            helper.close()
        }

    @Test
    fun `closing ends the helper process`() =
        run {
            val helper = helper()
            helper.call("a")
            helper.close()
            assertFalse(launched.single().isAlive)
        }
}
