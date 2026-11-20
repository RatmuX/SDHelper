package io.github.ratmux.sdhelper.ui.designapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeckIconSetTest {
    @Test
    fun `toggle actions show a different face when toggled`() {
        assertNotEquals(DeckIconSet.resolve("mic_off", true), DeckIconSet.resolve("mic_off", false))
        assertNotEquals(DeckIconSet.resolve("volume_off", true), DeckIconSet.resolve("volume_off", false))
        assertNotEquals(DeckIconSet.resolve("play_pause", true), DeckIconSet.resolve("play_pause", false))
        assertEquals(R.drawable.ic_deck_mic_off, DeckIconSet.resolve("mic_off", true))
        assertEquals(R.drawable.ic_deck_mic, DeckIconSet.resolve("mic_off", null))
        assertEquals(R.drawable.ic_deck_pause, DeckIconSet.resolve("play_pause", true))
    }

    @Test
    fun `plain icons ignore the toggle state`() {
        assertEquals(DeckIconSet.resolve("skip_next", true), DeckIconSet.resolve("skip_next", false))
        assertEquals(R.drawable.ic_deck_volume_up, DeckIconSet.resolve("volume_up", null))
    }

    @Test
    fun `unknown and missing keys fall back to the generic icon`() {
        assertEquals(R.drawable.ic_deck_apps, DeckIconSet.resolve(null, null))
        assertEquals(R.drawable.ic_deck_apps, DeckIconSet.resolve("something_new", true))
    }

    @Test
    fun `every key the agent's built-in actions use has its own icon`() {
        // the keys published by builtin-system-audio and builtin-media
        for (key in listOf(
            "volume_up",
            "volume_down",
            "volume_off",
            "mic_off",
            "play_pause",
            "skip_next",
            "skip_previous",
        )) {
            assertNotEquals(R.drawable.ic_deck_apps, DeckIconSet.resolve(key, true), key)
            assertNotEquals(R.drawable.ic_deck_apps, DeckIconSet.resolve(key, false), key)
        }
    }
}
