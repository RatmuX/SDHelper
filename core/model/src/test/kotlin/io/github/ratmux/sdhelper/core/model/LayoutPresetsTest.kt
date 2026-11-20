package io.github.ratmux.sdhelper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutPresetsTest {
    private val known =
        setOf(
            BuiltinActions.VOLUME_UP,
            BuiltinActions.VOLUME_DOWN,
            BuiltinActions.MUTE_OUTPUT,
            BuiltinActions.MUTE_MICROPHONE,
            BuiltinActions.PLAY_PAUSE,
            BuiltinActions.NEXT,
            BuiltinActions.PREVIOUS,
        )

    @Test
    fun `every preset builds a valid profile for every grid size`() {
        for (preset in LayoutPresets.all) {
            for (grid in GridSize.ALLOWED) {
                val profile = preset.create("en", grid)
                assertEquals(grid, profile.grid)
                assertEquals(preset.actionIds.size, profile.buttonCount, "${preset.id} on $grid keeps all buttons")
                assertTrue(profile.pages.size in 1..LayoutLimits.MAX_PAGES)
            }
        }
    }

    @Test
    fun `presets only use known built-in actions and no action twice`() {
        for (preset in LayoutPresets.all) {
            assertTrue(known.containsAll(preset.actionIds), preset.id)
            assertEquals(preset.actionIds.size, preset.actionIds.toSet().size, "${preset.id} repeats an action")
        }
    }

    @Test
    fun `presets have unique ids and texts in english and turkish`() {
        assertEquals(
            LayoutPresets.all.size,
            LayoutPresets.all
                .map { it.id }
                .toSet()
                .size,
        )
        for (preset in LayoutPresets.all) {
            assertNotEquals(preset.title("en"), preset.title("tr"))
            assertTrue(preset.description("tr").isNotBlank())
            assertEquals(preset.title("en"), preset.title("de"), "unknown languages fall back to english")
        }
        assertEquals(
            "tr",
            LayoutPresets
                .byId("gaming")
                ?.title
                ?.keys
                ?.first { it == "tr" },
        )
        assertNull(LayoutPresets.byId("nope"))
    }

    @Test
    fun `the first page carries applications and the second the system, as designed`() {
        val everyday = LayoutPresets.EVERYDAY.create("en")
        assertEquals("Apps and media", everyday.pages[0].name)
        assertEquals("System", everyday.pages[1].name)
        assertEquals(DeckSlot.Action(BuiltinActions.PLAY_PAUSE), everyday.slotAt(SlotPosition(0, 1)))
        assertEquals(DeckSlot.Action(BuiltinActions.MUTE_MICROPHONE), everyday.slotAt(SlotPosition(1, 3)))
        assertEquals("Günlük", LayoutPresets.EVERYDAY.create("tr").name)
    }

    @Test
    fun `streaming puts the microphone mute in the very first cell`() {
        assertEquals(
            DeckSlot.Action(BuiltinActions.MUTE_MICROPHONE),
            LayoutPresets.STREAMING.create("en").slotAt(SlotPosition(0, 0)),
        )
    }

    @Test
    fun `creating twice gives profiles and pages with different ids`() {
        val a = LayoutPresets.GAMING.create("en")
        val b = LayoutPresets.GAMING.create("en")
        assertNotEquals(a.id, b.id)
        assertTrue((a.pages.map { it.id } + b.pages.map { it.id }).toSet().size == a.pages.size + b.pages.size)
    }
}
