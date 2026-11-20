package io.github.ratmux.sdhelper.agent.platform.windows

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** A message the helper sent on its own, for example `audio.changed`. */
class Notification(
    val method: String,
    val params: JsonElement,
)

/** The helper answered with a JSON-RPC error. [code] is documented in native/windows/src/rpc.rs. */
class RpcException(
    val code: Int,
    message: String,
) : Exception(message) {
    /** The thing asked for does not exist right now (no microphone, no player). Expected in normal life. */
    val isUnavailable: Boolean get() = code == UNAVAILABLE

    companion object {
        const val UNAVAILABLE = -32001
        const val UNSUPPORTED = -32002
    }
}

/** The helper process is gone, or never started. */
class HelperClosedException(
    message: String = "The Windows helper is not running",
) : Exception(message)

class HelperTimeoutException : Exception("The Windows helper did not answer in time")

/**
 * JSON-RPC 2.0 over a pair of streams, one JSON object per line (the protocol of `native/windows`).
 * Requests are matched to answers by id, so several calls can be in flight; messages without an id are
 * notifications. When the input ends, every call still waiting fails with [HelperClosedException].
 */
class JsonRpcConnection(
    private val input: InputStream,
    private val output: OutputStream,
    scope: CoroutineScope,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : AutoCloseable {
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val ids = AtomicLong()
    private val writeLock = Mutex()
    private val mutableNotifications =
        MutableSharedFlow<Notification>(
            extraBufferCapacity = NOTIFICATION_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    @Volatile
    private var closed = false

    val notifications: SharedFlow<Notification> get() = mutableNotifications
    val isOpen: Boolean get() = !closed

    private val reader =
        scope.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).forEachLine(::handle)
            } catch (_: IOException) {
                // the stream was closed under us: same as end of input
            } finally {
                markClosed()
            }
        }

    /** Runs [action] once when the connection has ended, however it ended. */
    fun onClosed(action: () -> Unit) {
        reader.invokeOnCompletion { action() }
    }

    suspend fun call(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ): JsonElement {
        if (closed) throw HelperClosedException()
        val id = ids.incrementAndGet()
        val reply = CompletableDeferred<JsonElement>()
        pending[id] = reply
        try {
            send(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", method)
                    put("params", params)
                },
            )
            return withTimeoutOrNull(timeoutMillis) { reply.await() } ?: throw HelperTimeoutException()
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun send(message: JsonObject) {
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    output.write((message.toString() + "\n").toByteArray(Charsets.UTF_8))
                    output.flush()
                } catch (_: IOException) {
                    throw HelperClosedException()
                }
            }
        }
    }

    private fun handle(line: String) {
        // A line that is not valid JSON is ignored: one garbled line must not kill the connection.
        val message = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        val id = message["id"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
        val error = message["error"] as? JsonObject
        when {
            id != null && error != null -> {
                pending[id]?.completeExceptionally(
                    RpcException(
                        error["code"]?.jsonPrimitive?.int ?: 0,
                        error["message"]?.jsonPrimitive?.content.orEmpty(),
                    ),
                )
            }

            id != null && "result" in message -> {
                pending[id]?.complete(message.getValue("result"))
            }

            id == null && "method" in message -> {
                mutableNotifications.tryEmit(
                    Notification(message.getValue("method").jsonPrimitive.content, message["params"] ?: JsonNull),
                )
            }
        }
    }

    private fun markClosed() {
        closed = true
        val failure = HelperClosedException()
        pending.values.forEach { it.completeExceptionally(failure) }
    }

    override fun close() {
        closed = true
        runCatching { output.close() } // closing stdin is how the helper learns that the agent is gone
        runCatching { input.close() }
        reader.cancel()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val NOTIFICATION_BUFFER = 64
    }
}
