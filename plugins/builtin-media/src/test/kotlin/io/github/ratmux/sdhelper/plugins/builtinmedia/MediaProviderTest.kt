package io.github.ratmux.sdhelper.plugins.builtinmedia

import io.github.ratmux.sdhelper.agent.core.platform.InMemoryMediaController
import io.github.ratmux.sdhelper.agent.core.platform.MediaCommand
import io.github.ratmux.sdhelper.agent.core.platform.MediaController
import io.github.ratmux.sdhelper.agent.core.platform.MediaState
import io.github.ratmux.sdhelper.agent.core.platform.MediaStatus
import io.github.ratmux.sdhelper.agent.core.platform.PlatformException
import io.github.ratmux.sdhelper.core.pluginapi.ActionResult
import io.github.ratmux.sdhelper.core.pluginapi.ActionTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaProviderTest {
    private val media = InMemoryMediaController()
    private val provider = MediaProvider(media)

    private suspend fun states() = provider.states.first()

    @Test
    fun `actions are unique, localized and have icons`() {
        val actions = provider.actions()
        assertEquals(actions.size, actions.map { it.id }.toSet().size)
        actions.forEach {
            assertTrue(it.title["en"]!!.isNotBlank() && it.title["tr"]!!.isNotBlank(), it.id)
            assertTrue(it.icon != null, it.id)
        }
        assertEquals(listOf("play-pause"), actions.filter { it.toggle }.map { it.id })
    }

    @Test
    fun `play pause toggles and the state shows what is playing`() =
        runTest {
            assertEquals(false, states().getValue("play-pause").toggled)

            assertEquals(ActionResult.Success, provider.invoke(MediaProvider.PLAY_PAUSE, emptyMap()))

            val playing = states().getValue("play-pause")
            assertEquals(true, playing.toggled)
            assertEquals(ActionTone.ACTIVE, playing.tone)
            assertEquals("Demo Artist – Demo Track One", playing.label)
        }

    @Test
    fun `next and previous change the track`() =
        runTest {
            provider.invoke(MediaProvider.NEXT, emptyMap())
            assertEquals("Demo Track Two", media.state.first().title)
            provider.invoke(MediaProvider.PREVIOUS, emptyMap())
            assertEquals("Demo Track One", media.state.first().title)
        }

    @Test
    fun `without a player the actions are unavailable and failures are reported`() =
        runTest {
            val none =
                object : MediaController {
                    override val state: Flow<MediaState> = MutableStateFlow(MediaState(false, MediaStatus.NONE))

                    override suspend fun send(command: MediaCommand) =
                        throw PlatformException("No media player is running")
                }
            val empty = MediaProvider(none)

            empty.states
                .first()
                .values
                .forEach { assertEquals(false, it.available) }
            assertEquals(
                ActionResult.Failure("No media player is running"),
                empty.invoke(MediaProvider.NEXT, emptyMap()),
            )
        }

    @Test
    fun `unknown actions fail`() =
        runTest {
            assertEquals(ActionResult.Failure("unknown action"), provider.invoke("nope", emptyMap()))
        }
}
