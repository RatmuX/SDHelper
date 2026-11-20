package io.github.ratmux.sdhelper.plugins.builtinsystemaudio

import io.github.ratmux.sdhelper.agent.core.platform.AudioController
import io.github.ratmux.sdhelper.agent.core.platform.AudioDevice
import io.github.ratmux.sdhelper.agent.core.platform.PlatformException
import io.github.ratmux.sdhelper.core.pluginapi.ActionDescriptor
import io.github.ratmux.sdhelper.core.pluginapi.ActionProvider
import io.github.ratmux.sdhelper.core.pluginapi.ActionResult
import io.github.ratmux.sdhelper.core.pluginapi.ActionState
import io.github.ratmux.sdhelper.core.pluginapi.ActionTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Built-in actions for the system volume and microphone, on top of the [AudioController] port. */
class SystemAudioProvider(
    private val audio: AudioController,
) : ActionProvider {
    override val providerId: String = ID

    override fun actions(): List<ActionDescriptor> =
        listOf(
            ActionDescriptor(VOLUME_UP, mapOf("en" to "Volume up", "tr" to "Sesi artır"), "volume_up"),
            ActionDescriptor(VOLUME_DOWN, mapOf("en" to "Volume down", "tr" to "Sesi azalt"), "volume_down"),
            ActionDescriptor(
                MUTE_OUTPUT,
                mapOf("en" to "Mute speakers", "tr" to "Hoparlörü sustur"),
                "volume_off",
                toggle = true,
            ),
            ActionDescriptor(
                MUTE_MICROPHONE,
                mapOf("en" to "Mute microphone", "tr" to "Mikrofonu sustur"),
                "mic_off",
                toggle = true,
            ),
        )

    override val states: Flow<Map<String, ActionState>> =
        audio.state.map { audioState ->
            val output = audioState.output
            val input = audioState.input
            val volume = ActionState(available = output != null, label = output?.let { "${it.volumePercent}%" })
            mapOf(
                VOLUME_UP to volume,
                VOLUME_DOWN to volume,
                MUTE_OUTPUT to
                    ActionState(
                        available = output != null,
                        toggled = output?.muted,
                        tone = if (output?.muted == true) ActionTone.ALERT else ActionTone.NORMAL,
                    ),
                MUTE_MICROPHONE to
                    ActionState(
                        available = input != null,
                        toggled = input?.muted,
                        tone = if (input?.muted == true) ActionTone.ALERT else ActionTone.NORMAL,
                    ),
            )
        }

    override suspend fun invoke(
        actionId: String,
        params: Map<String, String>,
    ): ActionResult =
        try {
            when (actionId) {
                VOLUME_UP -> audio.changeVolume(+VOLUME_STEP_PERCENT)
                VOLUME_DOWN -> audio.changeVolume(-VOLUME_STEP_PERCENT)
                MUTE_OUTPUT -> audio.setMuted(AudioDevice.OUTPUT, null)
                MUTE_MICROPHONE -> audio.setMuted(AudioDevice.INPUT, null)
                else -> return ActionResult.Failure("unknown action")
            }
            ActionResult.Success
        } catch (e: PlatformException) {
            ActionResult.Failure(e.message ?: "audio is not available")
        }

    companion object {
        const val ID = "builtin.system-audio"
        const val VOLUME_UP = "volume-up"
        const val VOLUME_DOWN = "volume-down"
        const val MUTE_OUTPUT = "mute-output"
        const val MUTE_MICROPHONE = "mute-microphone"
        const val VOLUME_STEP_PERCENT = 5
    }
}
