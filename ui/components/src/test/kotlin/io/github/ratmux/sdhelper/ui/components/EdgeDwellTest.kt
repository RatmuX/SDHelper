package io.github.ratmux.sdhelper.ui.components

import io.github.ratmux.sdhelper.ui.components.EdgeDwell.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EdgeDwellTest {
    private val width = 1000f
    private val dwell = EdgeDwell(dwellMillis = 700, edgeFraction = 0.1f)

    @Test
    fun `nothing happens in the middle of the screen`() {
        assertNull(dwell.update(500f, width, 0))
        assertNull(dwell.update(500f, width, 10_000))
    }

    @Test
    fun `staying in the right edge zone for the dwell time turns to the next page`() {
        assertNull(dwell.update(950f, width, 0), "entering the zone only starts the timer")
        assertNull(dwell.update(960f, width, 699))
        assertEquals(Direction.NEXT, dwell.update(955f, width, 700))
    }

    @Test
    fun `the left edge goes back`() {
        dwell.update(20f, width, 0)
        assertEquals(Direction.PREVIOUS, dwell.update(30f, width, 800))
    }

    @Test
    fun `holding keeps paging once per dwell time`() {
        dwell.update(990f, width, 0)
        assertEquals(Direction.NEXT, dwell.update(990f, width, 700))
        assertNull(dwell.update(990f, width, 1_000))
        assertEquals(Direction.NEXT, dwell.update(990f, width, 1_400))
    }

    @Test
    fun `leaving the zone starts over, and switching sides restarts the timer`() {
        dwell.update(950f, width, 0)
        assertNull(dwell.update(500f, width, 600))
        assertNull(dwell.update(950f, width, 700), "back in the zone: the timer starts again")
        assertNull(dwell.update(950f, width, 1_300))
        assertEquals(Direction.NEXT, dwell.update(950f, width, 1_400))

        dwell.update(950f, width, 2_000)
        assertNull(dwell.update(10f, width, 2_100), "moved to the other edge: restart")
        assertNull(dwell.update(10f, width, 2_700))
        assertEquals(Direction.PREVIOUS, dwell.update(10f, width, 2_800))
    }

    @Test
    fun `reset and an unknown width are harmless`() {
        dwell.update(950f, width, 0)
        dwell.reset()
        assertNull(dwell.update(950f, width, 800), "after a reset the timer has to start again")
        assertNull(dwell.update(0f, 0f, 5_000))
    }
}
