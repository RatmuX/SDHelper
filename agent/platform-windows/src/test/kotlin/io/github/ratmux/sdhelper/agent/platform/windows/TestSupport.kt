package io.github.ratmux.sdhelper.agent.platform.windows

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** A one-way byte pipe with blocking reads and a clean end of stream (unlike `PipedInputStream`). */
class Pipe {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val end = ByteArray(0)
    private var current: ByteArray = ByteArray(0)
    private var position = 0

    @Volatile
    private var finished = false

    val output: OutputStream =
        object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
                if (finished) throw java.io.IOException("pipe closed")
                queue.put(b.copyOfRange(off, off + len))
            }

            override fun close() = finish()
        }

    val input: InputStream =
        object : InputStream() {
            override fun read(): Int {
                while (position >= current.size) {
                    if (finished && queue.isEmpty()) return -1
                    val next = queue.take()
                    if (next === end) return -1
                    current = next
                    position = 0
                }
                return current[position++].toInt() and 0xFF
            }

            /** Like a real process stream: waits for the first byte, then returns what is already there. */
            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
                if (len == 0) return 0
                val first = read()
                if (first == -1) return -1
                b[off] = first.toByte()
                var count = 1
                while (count < len && position < current.size) b[off + count++] = current[position++]
                return count
            }

            override fun close() = finish()
        }

    private fun finish() {
        if (!finished) {
            finished = true
            queue.put(end)
        }
    }
}

/**
 * A helper process that lives inside the test: it reads the agent's lines, answers them with [handler] (return a
 * JSON element, or throw [FakeRpcError]), and can send notifications or die.
 */
class FakeHelperProcess(
    private val handler: (method: String, params: JsonObject) -> JsonElement,
) : HelperProcess {
    private val toHelper = Pipe()
    private val fromHelper = Pipe()
    private val alive = AtomicBoolean(true)
    val received = LinkedBlockingQueue<String>()

    override val input: InputStream get() = fromHelper.input
    override val output: OutputStream get() = toHelper.output
    override val isAlive: Boolean get() = alive.get()

    private val thread =
        Thread {
            runCatching {
                toHelper.input.bufferedReader().forEachLine { line ->
                    val request = Json.parseToJsonElement(line).jsonObject
                    val method = request.getValue("method").jsonPrimitive.content
                    received.put(method)
                    val id = request["id"] ?: return@forEachLine
                    val reply =
                        try {
                            buildJsonObject {
                                put("jsonrpc", "2.0")
                                put("id", id)
                                put("result", handler(method, request["params"]?.jsonObject ?: JsonObject(emptyMap())))
                            }
                        } catch (e: FakeRpcError) {
                            buildJsonObject {
                                put("jsonrpc", "2.0")
                                put("id", id)
                                put(
                                    "error",
                                    buildJsonObject {
                                        put("code", e.code)
                                        put("message", e.message.orEmpty())
                                    },
                                )
                            }
                        }
                    send(reply.toString())
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

    fun send(line: String) {
        fromHelper.output.write((line + "\n").toByteArray())
    }

    fun notify(
        method: String,
        params: JsonElement = JsonNull,
    ) = send(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }.toString(),
    )

    /** Simulates the helper dying: the agent sees the end of its output. */
    fun crash() {
        alive.set(false)
        fromHelper.output.close()
        toHelper.input.close()
        thread.interrupt()
    }

    override fun destroy() = crash()
}

class FakeRpcError(
    val code: Int,
    message: String,
) : Exception(message)

/** A [HelperRpc] with canned answers, for the classes that sit on top of the connection. */
class FakeHelperRpc(
    var handler: (method: String, params: JsonObject) -> JsonElement = { _, _ -> JsonNull },
) : HelperRpc {
    private val shared = MutableSharedFlow<Notification>(extraBufferCapacity = 16)
    val calls = mutableListOf<Pair<String, JsonObject>>()

    override val notifications: SharedFlow<Notification> get() = shared

    override suspend fun call(
        method: String,
        params: JsonObject,
    ): JsonElement {
        calls += method to params
        try {
            return handler(method, params)
        } catch (e: FakeRpcError) {
            throw RpcException(e.code, e.message.orEmpty())
        }
    }

    suspend fun emit(
        method: String,
        params: JsonElement = JsonNull,
    ) = shared.emit(Notification(method, params))
}

fun json(text: String): JsonElement = Json.parseToJsonElement(text)

fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
