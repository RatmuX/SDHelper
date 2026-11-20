package io.github.ratmux.sdhelper.agent.platform.windows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonRpcConnectionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val toClose = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() {
        toClose.forEach { it.close() }
        scope.cancel()
    }

    private fun connect(
        timeoutMillis: Long = 5_000,
        handler: (String, JsonObject) -> kotlinx.serialization.json.JsonElement = { _, _ -> json("{}") },
    ): Pair<JsonRpcConnection, FakeHelperProcess> {
        val helper = FakeHelperProcess(handler)
        val connection = JsonRpcConnection(helper.input, helper.output, scope, timeoutMillis)
        toClose += connection
        return connection to helper
    }

    private fun run(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(15_000) { block() } }

    @Test
    fun `a call returns what the helper answered`() =
        run {
            val (connection, _) =
                connect { method, params ->
                    buildJsonObject {
                        put(
                            "echo",
                            method + params["x"]?.jsonPrimitive?.content,
                        )
                    }
                }
            val answer = connection.call("ping", buildJsonObject { put("x", "1") })
            assertEquals("ping1", (answer as JsonObject)["echo"]?.jsonPrimitive?.content)
        }

    @Test
    fun `answers are matched by id even when they arrive out of order`() =
        run {
            val gate = CountDownLatch(1)
            val (connection, _) =
                connect { method, _ ->
                    if (method == "slow") gate.await(5, TimeUnit.SECONDS)
                    buildJsonObject { put("method", method) }
                }
            val slow = async { connection.call("slow") }
            val fast = connection.call("fast")
            assertEquals("fast", (fast as JsonObject)["method"]?.jsonPrimitive?.content)
            gate.countDown()
            assertEquals("slow", (slow.await() as JsonObject)["method"]?.jsonPrimitive?.content)
        }

    @Test
    fun `an error answer becomes an RpcException with its code`() =
        run {
            val (connection, _) = connect { _, _ -> throw FakeRpcError(RpcException.UNAVAILABLE, "no microphone") }
            val error = assertFailsWith<RpcException> { connection.call("audio.setMute") }
            assertEquals(RpcException.UNAVAILABLE, error.code)
            assertEquals("no microphone", error.message)
            assertTrue(error.isUnavailable)
        }

    @Test
    fun `notifications are delivered to collectors`() =
        run {
            val (connection, helper) = connect()
            val seen =
                async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { connection.notifications.first() }
            helper.notify("audio.changed", json("""{"output":{"volume":7,"muted":false}}"""))
            val notification = seen.await()
            assertEquals("audio.changed", notification.method)
        }

    @Test
    fun `garbage lines do not break the connection`() =
        run {
            val (connection, helper) = connect()
            helper.send("this is not json")
            helper.send("[1,2,3]")
            helper.send("""{"unrelated":true}""")
            assertTrue(connection.call("still-works") is JsonObject)
            assertTrue(connection.isOpen)
        }

    @Test
    fun `when the helper dies waiting calls fail and later calls fail fast`() =
        run {
            val release = CountDownLatch(1)
            val (connection, helper) =
                connect { _, _ ->
                    release.await(5, TimeUnit.SECONDS)
                    json("{}")
                }
            val waiting = async { runCatching { connection.call("hangs") } }
            while (helper.received.isEmpty()) kotlinx.coroutines.delay(10)

            helper.crash()
            release.countDown()

            assertTrue(waiting.await().exceptionOrNull() is HelperClosedException)
            assertFailsWith<HelperClosedException> { connection.call("after") }
            assertFalse(connection.isOpen)
        }

    @Test
    fun `a helper that never answers causes a timeout and the connection stays usable`() =
        run {
            val (connection, _) =
                connect(timeoutMillis = 200) { method, _ ->
                    if (method == "never") Thread.sleep(1_000)
                    json("{}")
                }
            assertFailsWith<HelperTimeoutException> { connection.call("never") }
            assertTrue(connection.isOpen)
        }

    @Test
    fun `the closed callback runs once when the helper ends`() =
        run {
            val (connection, helper) = connect()
            val latch = CountDownLatch(1)
            connection.onClosed { latch.countDown() }
            helper.crash()
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        }
}
