package io.github.ratmux.sdhelper.plugins.builtinmedia

import io.github.ratmux.sdhelper.agent.core.platform.MediaCommand
import io.github.ratmux.sdhelper.agent.core.platform.MediaController
import io.github.ratmux.sdhelper.agent.core.platform.MediaState
import io.github.ratmux.sdhelper.agent.core.platform.MediaStatus
import io.github.ratmux.sdhelper.agent.core.platform.PlatformException
import io.github.ratmux.sdhelper.core.pluginapi.ActionDescriptor
import io.github.ratmux.sdhelper.core.pluginapi.ActionProvider
import io.github.ratmux.sdhelper.core.pluginapi.ActionResult
import io.github.ratmux.sdhelper.core.pluginapi.ActionState
import io.github.ratmux.sdhelper.core.pluginapi.ActionTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Built-in actions for the media player the system exposes (Spotify, browsers, ...), via the [MediaController] port. */
class MediaProvider(
    private val media: MediaController,
) : ActionProvider {
    override val providerId: String = ID

    override fun actions(): List<ActionDescriptor> =
        listOf(
            ActionDescriptor(
                PLAY_PAUSE,
                mapOf("en" to "Play / pause", "tr" to "Oynat / duraklat"),
                "play_pause",
                toggle = true,
            ),
            ActionDescriptor(NEXT, mapOf("en" to "Next track", "tr" to "Sonraki parça"), "skip_next"),
            ActionDescriptor(PREVIOUS, mapOf("en" to "Previous track", "tr" to "Önceki parça"), "skip_previous"),
        )

    override val states: Flow<Map<String, ActionState>> =
        media.state.map { state ->
            val playing = state.status == MediaStatus.PLAYING
            val skip = ActionState(available = state.available)
            mapOf(
                PLAY_PAUSE to
                    ActionState(
                        available = state.available,
                        toggled = playing,
                        label = nowPlaying(state),
                        tone = if (playing) ActionTone.ACTIVE else ActionTone.NORMAL,
                    ),
                NEXT to skip,
                PREVIOUS to skip,
            )
        }

    override suspend fun invoke(
        actionId: String,
        params: Map<String, String>,
    ): ActionResult {
        val command =
            when (actionId) {
                PLAY_PAUSE -> MediaCommand.PLAY_PAUSE
                NEXT -> MediaCommand.NEXT
                PREVIOUS -> MediaCommand.PREVIOUS
                else -> return ActionResult.Failure("unknown action")
            }
        return try {
            media.send(command)
            ActionResult.Success
        } catch (e: PlatformException) {
            ActionResult.Failure(e.message ?: "no media player is available")
        }
    }

    private fun nowPlaying(state: MediaState): String? =
        listOfNotNull(state.artist, state.title).filter { it.isNotBlank() }.joinToString(" – ").ifEmpty { null }

    companion object {
        const val ID = "builtin.media"
        const val PLAY_PAUSE = "play-pause"
        const val NEXT = "next"
        const val PREVIOUS = "previous"
    }
}
