package io.github.ratmux.sdhelper.plugins.builtinsystemaudio

import io.github.ratmux.sdhelper.agent.core.platform.AudioState
import io.github.ratmux.sdhelper.agent.core.platform.InMemoryAudioController
import io.github.ratmux.sdhelper.core.pluginapi.ActionResult
import io.github.ratmux.sdhelper.core.pluginapi.ActionTone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemAudioProviderTest {
    private val audio = InMemoryAudioController(initialVolume = 50)
    private val provider = SystemAudioProvider(audio)

    private suspend fun states() = provider.states.first()

    @Test
    fun `actions are unique, localized and have icons`() {
        val actions = provider.actions()
        assertEquals(actions.size, actions.map { it.id }.toSet().size)
        actions.forEach {
            assertTrue(it.title["en"]!!.isNotBlank() && it.title["tr"]!!.isNotBlank(), it.id)
            assertTrue(it.icon != null, it.id)
        }
        assertEquals(setOf("mute-output", "mute-microphone"), actions.filter { it.toggle }.map { it.id }.toSet())
    }

    @Test
    fun `volume actions move the volume in steps and show the level`() =
        runTest {
            assertEquals(ActionResult.Success, provider.invoke(SystemAudioProvider.VOLUME_UP, emptyMap()))
            assertEquals(
                55,
                audio.state
                    .first()
                    .output
                    ?.volumePercent,
            )
            assertEquals("55%", states().getValue("volume-up").label)

            provider.invoke(SystemAudioProvider.VOLUME_DOWN, emptyMap())
            provider.invoke(SystemAudioProvider.VOLUME_DOWN, emptyMap())
            assertEquals(
                45,
                audio.state
                    .first()
                    .output
                    ?.volumePercent,
            )
        }

    @Test
    fun `mute actions toggle and alert while muted`() =
        runTest {
            provider.invoke(SystemAudioProvider.MUTE_MICROPHONE, emptyMap())
            val muted = states().getValue("mute-microphone")
            assertEquals(true, muted.toggled)
            assertEquals(ActionTone.ALERT, muted.tone)

            provider.invoke(SystemAudioProvider.MUTE_MICROPHONE, emptyMap())
            assertEquals(ActionTone.NORMAL, states().getValue("mute-microphone").tone)

            provider.invoke(SystemAudioProvider.MUTE_OUTPUT, emptyMap())
            assertEquals(true, states().getValue("mute-output").toggled)
        }

    @Test
    fun `missing devices make the actions unavailable and report a clear failure`() =
        runTest {
            audio.setState(AudioState(output = null, input = null))

            states().values.forEach { assertEquals(false, it.available) }
            assertNull(states().getValue("mute-microphone").toggled)
            val failure =
                assertIs<ActionResult.Failure>(provider.invoke(SystemAudioProvider.MUTE_MICROPHONE, emptyMap()))
            assertEquals("No microphone", failure.message)
        }

    @Test
    fun `unknown actions fail`() =
        runTest {
            assertEquals(ActionResult.Failure("unknown action"), provider.invoke("nope", emptyMap()))
        }
}
