package io.github.ratmux.sdhelper.agent.platform.windows

import io.github.ratmux.sdhelper.agent.core.platform.AudioController
import io.github.ratmux.sdhelper.agent.core.platform.AudioDevice
import io.github.ratmux.sdhelper.agent.core.platform.AudioState
import io.github.ratmux.sdhelper.agent.core.platform.InputEndpoint
import io.github.ratmux.sdhelper.agent.core.platform.MediaCommand
import io.github.ratmux.sdhelper.agent.core.platform.MediaController
import io.github.ratmux.sdhelper.agent.core.platform.MediaState
import io.github.ratmux.sdhelper.agent.core.platform.MediaStatus
import io.github.ratmux.sdhelper.agent.core.platform.OutputEndpoint
import io.github.ratmux.sdhelper.agent.core.platform.PlatformException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class OutputDto(
    val volume: Int,
    val muted: Boolean,
)

@Serializable
private data class InputDto(
    val muted: Boolean,
)

@Serializable
private data class AudioDto(
    val output: OutputDto? = null,
    val input: InputDto? = null,
)

@Serializable
private data class MediaDto(
    val available: Boolean,
    val status: String,
    val title: String? = null,
    val artist: String? = null,
)

private fun parseAudio(element: JsonElement): AudioState {
    val dto = json.decodeFromJsonElement(AudioDto.serializer(), element)
    return AudioState(
        output = dto.output?.let { OutputEndpoint(it.volume.coerceIn(0, MAX_PERCENT), it.muted) },
        input = dto.input?.let { InputEndpoint(it.muted) },
    )
}

private fun parseMedia(element: JsonElement): MediaState {
    val dto = json.decodeFromJsonElement(MediaDto.serializer(), element)
    val status =
        when (dto.status) {
            "playing" -> MediaStatus.PLAYING
            "paused" -> MediaStatus.PAUSED
            "stopped" -> MediaStatus.STOPPED
            else -> MediaStatus.NONE
        }
    return MediaState(dto.available, status, dto.title, dto.artist)
}

private const val MAX_PERCENT = 100

/** Turns helper failures into what the actions report to the person pressing a button. */
private suspend fun <T> reporting(block: suspend () -> T): T =
    try {
        block()
    } catch (e: RpcException) {
        throw PlatformException(
            if (e.isUnavailable) {
                e.message.orEmpty().replaceFirstChar {
                    it.uppercase()
                }
            } else {
                "The system could not do that"
            },
            e,
        )
    } catch (e: HelperClosedException) {
        throw PlatformException("The Windows helper is not running", e)
    } catch (e: HelperTimeoutException) {
        throw PlatformException("The Windows helper did not answer", e)
    }

/**
 * Keeps the latest state the helper reported. Until the helper has answered, and after it died, the state is
 * [unavailable]; a dead helper is started again with growing pauses until it answers.
 */
internal class HelperState<T : Any>(
    private val helper: HelperRpc,
    private val scope: CoroutineScope,
    private val getMethod: String,
    private val changedMethod: String,
    private val parse: (JsonElement) -> T,
    private val unavailable: T,
) {
    private val current = MutableStateFlow(unavailable)
    private val recovering = AtomicBoolean(false)

    val flow: Flow<T> get() = current

    init {
        scope.launch {
            helper.notifications.collect { notification ->
                when (notification.method) {
                    changedMethod -> {
                        runCatching { parse(notification.params) }.onSuccess { current.value = it }
                    }

                    HELPER_CLOSED -> {
                        current.value = unavailable
                        recover()
                    }
                }
            }
        }
        recover()
    }

    fun update(value: T) {
        current.value = value
    }

    private fun recover() {
        if (!recovering.compareAndSet(false, true)) return
        scope.launch {
            var pause = FIRST_PAUSE_MILLIS
            try {
                while (true) {
                    try {
                        current.value = parse(helper.call(getMethod))
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        delay(pause)
                        pause = (pause * 2).coerceAtMost(MAX_PAUSE_MILLIS)
                    }
                }
            } finally {
                recovering.set(false)
            }
        }
    }

    private companion object {
        const val FIRST_PAUSE_MILLIS = 1_000L
        const val MAX_PAUSE_MILLIS = 30_000L
    }
}

/** [AudioController] backed by the native helper (Core Audio). */
class WindowsAudioController(
    private val helper: HelperRpc,
    scope: CoroutineScope,
) : AudioController {
    private val states =
        HelperState(helper, scope, "audio.get", "audio.changed", ::parseAudio, AudioState(output = null, input = null))

    override val state: Flow<AudioState> get() = states.flow

    override suspend fun changeVolume(deltaPercent: Int) {
        val answer =
            reporting { helper.call("audio.setVolume", buildJsonObject { put("delta", deltaPercent) }) }
        states.update(parseAudio(answer))
    }

    override suspend fun setMuted(
        device: AudioDevice,
        muted: Boolean?,
    ) {
        val params: JsonObject =
            buildJsonObject {
                put("device", if (device == AudioDevice.OUTPUT) "output" else "input")
                put(
                    "mode",
                    if (muted == null) {
                        "toggle"
                    } else if (muted) {
                        "on"
                    } else {
                        "off"
                    },
                )
            }
        states.update(parseAudio(reporting { helper.call("audio.setMute", params) }))
    }
}

/** [MediaController] backed by the native helper (System Media Transport Controls). */
class WindowsMediaController(
    private val helper: HelperRpc,
    scope: CoroutineScope,
) : MediaController {
    private val states =
        HelperState(helper, scope, "media.get", "media.changed", ::parseMedia, MediaState(false, MediaStatus.NONE))

    override val state: Flow<MediaState> get() = states.flow

    override suspend fun send(command: MediaCommand) {
        val name =
            when (command) {
                MediaCommand.PLAY_PAUSE -> "playPause"
                MediaCommand.NEXT -> "next"
                MediaCommand.PREVIOUS -> "previous"
            }
        val answer = reporting { helper.call("media.command", buildJsonObject { put("command", name) }) }
        states.update(parseMedia(answer))
    }
}
