package io.github.ratmux.sdhelper.agent.core.platform

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemoryControllersTest {
    @Test
    fun `volume is clamped to the valid range`() =
        runTest {
            val audio = InMemoryAudioController(initialVolume = 97)
            audio.changeVolume(+5)
            assertEquals(
                100,
                audio.state
                    .first()
                    .output
                    ?.volumePercent,
            )
            audio.changeVolume(-250)
            assertEquals(
                0,
                audio.state
                    .first()
                    .output
                    ?.volumePercent,
            )
        }

    @Test
    fun `mute can be toggled or set explicitly for each device`() =
        runTest {
            val audio = InMemoryAudioController()
            audio.setMuted(AudioDevice.INPUT, null)
            assertEquals(
                true,
                audio.state
                    .first()
                    .input
                    ?.muted,
            )
            audio.setMuted(AudioDevice.INPUT, null)
            assertEquals(
                false,
                audio.state
                    .first()
                    .input
                    ?.muted,
            )
            audio.setMuted(AudioDevice.OUTPUT, true)
            audio.setMuted(AudioDevice.OUTPUT, true)
            assertEquals(
                true,
                audio.state
                    .first()
                    .output
                    ?.muted,
            )
        }

    @Test
    fun `missing devices are reported`() =
        runTest {
            val audio = InMemoryAudioController()
            audio.setState(AudioState(output = null, input = null))
            assertFailsWith<PlatformException> { audio.changeVolume(5) }
            assertFailsWith<PlatformException> { audio.setMuted(AudioDevice.INPUT, null) }
        }

    @Test
    fun `media player plays, pauses and skips around the playlist`() =
        runTest {
            val media = InMemoryMediaController()
            assertEquals(MediaStatus.PAUSED, media.state.first().status)

            media.send(MediaCommand.PLAY_PAUSE)
            assertEquals(MediaStatus.PLAYING, media.state.first().status)

            media.send(MediaCommand.NEXT)
            assertEquals("Demo Track Two", media.state.first().title)
            media.send(MediaCommand.PREVIOUS)
            media.send(MediaCommand.PREVIOUS)
            assertEquals("Demo Track Three", media.state.first().title)
            assertEquals(MediaStatus.PLAYING, media.state.first().status)
        }
}
