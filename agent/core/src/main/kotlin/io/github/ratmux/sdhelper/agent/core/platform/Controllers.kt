package io.github.ratmux.sdhelper.agent.core.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The operating system could not do what was asked, for example because no microphone is present. */
class PlatformException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class AudioDevice { OUTPUT, INPUT }

data class OutputEndpoint(
    val volumePercent: Int,
    val muted: Boolean,
)

data class InputEndpoint(
    val muted: Boolean,
)

/** `null` means there is no default device of that kind right now. */
data class AudioState(
    val output: OutputEndpoint?,
    val input: InputEndpoint?,
)

/** Port: system audio. Windows implements it through the native helper (docs/PORTING_LINUX.md lists the others). */
interface AudioController {
    /** Current state first, then every change. */
    val state: Flow<AudioState>

    /** Raises or lowers the output volume by [deltaPercent] points, clamped to 0..100. */
    suspend fun changeVolume(deltaPercent: Int)

    /** Mutes or unmutes [device]; `null` toggles. */
    suspend fun setMuted(
        device: AudioDevice,
        muted: Boolean?,
    )
}

enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS }

enum class MediaStatus { NONE, PLAYING, PAUSED, STOPPED }

data class MediaState(
    val available: Boolean,
    val status: MediaStatus,
    val title: String? = null,
    val artist: String? = null,
)

/** Port: media playback control of whatever player the system exposes. */
interface MediaController {
    val state: Flow<MediaState>

    suspend fun send(command: MediaCommand)
}

/** In-memory audio for demos (`--demo-platform`) and as the test double of the real controllers. */
class InMemoryAudioController(
    initialVolume: Int = DEFAULT_VOLUME,
) : AudioController {
    private val current =
        MutableStateFlow(AudioState(OutputEndpoint(initialVolume, muted = false), InputEndpoint(muted = false)))

    override val state: Flow<AudioState> get() = current

    override suspend fun changeVolume(deltaPercent: Int) {
        current.update { state ->
            val output = state.output ?: throw PlatformException("No audio output device")
            state.copy(
                output = output.copy(volumePercent = (output.volumePercent + deltaPercent).coerceIn(0, MAX_VOLUME)),
            )
        }
    }

    override suspend fun setMuted(
        device: AudioDevice,
        muted: Boolean?,
    ) {
        current.update { state ->
            when (device) {
                AudioDevice.OUTPUT -> {
                    val output = state.output ?: throw PlatformException("No audio output device")
                    state.copy(output = output.copy(muted = muted ?: !output.muted))
                }

                AudioDevice.INPUT -> {
                    val input = state.input ?: throw PlatformException("No microphone")
                    state.copy(input = input.copy(muted = muted ?: !input.muted))
                }
            }
        }
    }

    /** Test helper: simulates unplugging devices. */
    fun setState(state: AudioState) {
        current.value = state
    }

    private companion object {
        const val DEFAULT_VOLUME = 50
        const val MAX_VOLUME = 100
    }
}

/** In-memory media player for demos and tests: three tracks that can be played, paused and skipped. */
class InMemoryMediaController : MediaController {
    private val tracks = listOf("Demo Track One", "Demo Track Two", "Demo Track Three")
    private var index = 0
    private val current = MutableStateFlow(stateFor(MediaStatus.PAUSED))

    override val state: Flow<MediaState> get() = current

    override suspend fun send(command: MediaCommand) {
        current.update { state ->
            when (command) {
                MediaCommand.PLAY_PAUSE -> {
                    stateFor(if (state.status == MediaStatus.PLAYING) MediaStatus.PAUSED else MediaStatus.PLAYING)
                }

                MediaCommand.NEXT -> {
                    index = (index + 1) % tracks.size
                    stateFor(state.status)
                }

                MediaCommand.PREVIOUS -> {
                    index = (index - 1 + tracks.size) % tracks.size
                    stateFor(state.status)
                }
            }
        }
    }

    private fun stateFor(status: MediaStatus) = MediaState(true, status, tracks[index], "Demo Artist")
}
