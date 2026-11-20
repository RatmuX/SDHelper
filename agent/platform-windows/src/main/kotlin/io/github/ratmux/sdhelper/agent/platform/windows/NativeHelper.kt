package io.github.ratmux.sdhelper.agent.platform.windows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock

/** What the agent talks to: a helper that answers calls and sends notifications. */
interface HelperRpc {
    /** Notifications of every helper process the agent has started; `helper.closed` when one ends. */
    val notifications: SharedFlow<Notification>

    suspend fun call(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ): JsonElement
}

/** A running helper process, as far as the agent cares. */
interface HelperProcess {
    /** What the helper writes (its stdout). */
    val input: InputStream

    /** What the helper reads (its stdin). */
    val output: OutputStream
    val isAlive: Boolean

    fun destroy()
}

fun interface ProcessLauncher {
    fun launch(): HelperProcess
}

/** Starts the real `sdhelper-native` executable. Its stderr goes to the agent's stderr, where its logs belong. */
class ExecutableLauncher(
    private val executable: Path,
) : ProcessLauncher {
    override fun launch(): HelperProcess {
        val process =
            ProcessBuilder(executable.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        return object : HelperProcess {
            override val input: InputStream get() = process.inputStream
            override val output: OutputStream get() = process.outputStream
            override val isAlive: Boolean get() = process.isAlive

            override fun destroy() {
                process.destroy()
            }
        }
    }
}

const val HELPER_CLOSED = "helper.closed"

/**
 * Owns the helper process: starts it on first use, starts it again when it died (at most [maxStartsPerMinute]
 * times a minute, so a broken helper cannot spin), and ends it when the agent stops.
 * A call that was already sent when the helper died fails; it is not repeated, because it might have taken
 * effect (a toggle repeated is a toggle undone).
 */
class NativeHelper(
    private val launcher: ProcessLauncher,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = 10_000,
    private val maxStartsPerMinute: Int = 5,
    private val clock: Clock = Clock.systemUTC(),
) : HelperRpc,
    AutoCloseable {
    private val startLock = Mutex()
    private val startTimes = ArrayDeque<Long>()
    private val relay =
        MutableSharedFlow<Notification>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var process: HelperProcess? = null
    private var connection: JsonRpcConnection? = null

    override val notifications: SharedFlow<Notification> get() = relay

    override suspend fun call(
        method: String,
        params: JsonObject,
    ): JsonElement = connection().call(method, params)

    private suspend fun connection(): JsonRpcConnection =
        startLock.withLock {
            val current = connection
            if (current != null && current.isOpen && process?.isAlive == true) return current
            start()
        }

    private fun start(): JsonRpcConnection {
        stopCurrent()
        val now = clock.millis()
        while (startTimes.isNotEmpty() && now - startTimes.first() > MINUTE_MILLIS) startTimes.removeFirst()
        if (startTimes.size >= maxStartsPerMinute) {
            throw HelperClosedException("The Windows helper keeps stopping; giving up for a minute")
        }
        startTimes.addLast(now)

        val started =
            try {
                launcher.launch()
            } catch (e: java.io.IOException) {
                throw HelperClosedException("The Windows helper could not be started: ${e.message}")
            }
        val opened = JsonRpcConnection(started.input, started.output, scope, timeoutMillis)
        process = started
        connection = opened
        scope.launch { opened.notifications.collect { relay.emit(it) } }
        opened.onClosed { relay.tryEmit(Notification(HELPER_CLOSED, JsonNull)) }
        return opened
    }

    private fun stopCurrent() {
        connection?.close()
        process?.destroy()
        connection = null
        process = null
    }

    override fun close() {
        stopCurrent()
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

/** Finds `sdhelper-native.exe` next to the agent, or where a developer built it. */
object NativeHelperLocator {
    const val ENV = "SDHELPER_NATIVE_PATH"
    private const val EXECUTABLE = "sdhelper-native.exe"
    private const val DEV_BUILD = "native/windows/target/release/sdhelper-native.exe"

    /** Candidates in order: the environment variable, the folder of the jpackage launcher, the working directory, the dev build. */
    fun candidates(
        env: Map<String, String> = System.getenv(),
        launcherPath: String? = System.getProperty("jpackage.app-path"),
        workingDirectory: Path = Paths.get("").toAbsolutePath(),
    ): List<Path> =
        listOfNotNull(
            env[ENV]?.takeIf { it.isNotBlank() }?.let { Paths.get(it) },
            launcherPath?.let { Paths.get(it).parent?.resolve(EXECUTABLE) },
            workingDirectory.resolve(EXECUTABLE),
            workingDirectory.resolve(DEV_BUILD),
        )

    fun find(candidates: List<Path> = candidates()): Path? = candidates.firstOrNull { Files.isRegularFile(it) }
}
