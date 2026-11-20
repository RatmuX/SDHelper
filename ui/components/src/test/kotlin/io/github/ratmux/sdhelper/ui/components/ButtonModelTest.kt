package io.github.ratmux.sdhelper.ui.components

import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActionTone
import io.github.ratmux.sdhelper.ui.designapi.DeckTileTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonModelTest {
    private val mic = DeckAction("builtin.system-audio/mute-microphone", "Mute microphone", "mic_off", toggle = true)
    private val known = mapOf(mic.id to mic)

    @Test
    fun `a known action that the computer offers is available and carries its live state`() {
        val state = DeckActionState(toggled = true, tone = DeckActionTone.ALERT)
        val model = ButtonModels.build(mic.id, known, mapOf(mic.id to state), setOf(mic.id), connected = true)

        assertTrue(model.available)
        assertTrue(model.known)
        assertEquals("Mute microphone", model.title)
        assertEquals("mic_off", model.iconKey)
        assertEquals(state, model.state)
    }

    @Test
    fun `without a connection the button is shown but not available`() {
        val model = ButtonModels.build(mic.id, known, emptyMap(), setOf(mic.id), connected = false)
        assertFalse(model.available)
        assertEquals("Mute microphone", model.title, "titles come from the cache, so layouts still read offline")
    }

    @Test
    fun `an action the computer does not offer, or reports as unavailable, is not available`() {
        assertFalse(ButtonModels.build(mic.id, known, emptyMap(), emptySet(), connected = true).available)
        val unavailable = mapOf(mic.id to DeckActionState(available = false))
        assertFalse(ButtonModels.build(mic.id, known, unavailable, setOf(mic.id), connected = true).available)
    }

    @Test
    fun `an action the phone has never seen still gets a readable title`() {
        val model =
            ButtonModels.build(
                "obs.studio/start-recording",
                emptyMap(),
                emptyMap(),
                emptySet(),
                connected = true,
            )
        assertFalse(model.known)
        assertEquals("Start recording", model.title)
        assertEquals("Play pause", ButtonModels.fallbackTitle("builtin.media/play-pause"))
        assertEquals("Odd name", ButtonModels.fallbackTitle("odd_name"))
    }

    @Test
    fun `tones map one to one`() {
        assertEquals(DeckTileTone.ALERT, DeckActionTone.ALERT.toTileTone())
        assertEquals(DeckActionTone.entries.size, DeckTileTone.entries.size)
        for (tone in DeckActionTone.entries) assertEquals(tone.name, tone.toTileTone().name)
    }
}
