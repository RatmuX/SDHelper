package io.github.ratmux.sdhelper.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridMathTest {
    private val gap = 10f

    @Test
    fun `tiles are square and as large as the tighter dimension allows`() {
        // wide area: height limits. 2 rows: (500 - 10) / 2 = 245
        val wide = GridMath.geometry(2000f, 500f, columns = 4, rows = 2, gap = gap)
        assertEquals(245f, wide.tile)

        // narrow area: width limits. 4 columns: (400 - 30) / 4 = 92.5
        val narrow = GridMath.geometry(400f, 2000f, columns = 4, rows = 2, gap = gap)
        assertEquals(92.5f, narrow.tile)
    }

    @Test
    fun `the grid is centred in the free space`() {
        val g = GridMath.geometry(2000f, 500f, 4, 2, gap)
        assertEquals((2000f - g.width) / 2, g.left)
        assertEquals(0f, g.top, 0.001f)
        assertEquals(g.left, g.cellLeft(0))
        assertEquals(g.left + 245f + gap, g.cellLeft(1))
        assertEquals(g.top + 245f + gap, g.cellTop(1))
    }

    @Test
    fun `every grid size fits a typical landscape phone with large buttons`() {
        // 20:9 phone in landscape with the system bars hidden, minus a slim header: about 2300 x 900 px
        for ((columns, rows) in listOf(3 to 2, 4 to 2, 5 to 2, 4 to 3, 6 to 3)) {
            val g = GridMath.geometry(2300f, 900f, columns, rows, 24f)
            assertTrue(g.width <= 2300f && g.height <= 900f, "$columns x $rows fits")
            assertTrue(g.tile >= 200f, "$columns x $rows keeps buttons big, was ${g.tile}")
        }
        assertTrue(GridMath.geometry(2300f, 900f, 4, 2, 24f).tile > 400f, "the default 4x2 gives really large buttons")
    }

    @Test
    fun `a page is never larger than the space it was given`() {
        val g = GridMath.geometry(300f, 100f, 6, 3, 20f)
        assertTrue(g.width <= 300f + 0.01f && g.height <= 100f + 0.01f)
        assertTrue(GridMath.geometry(10f, 10f, 6, 3, 20f).tile == 0f, "no room means no negative sizes")
    }

    @Test
    fun `hit testing finds the cell under a point, including the gaps around a button`() {
        val g = GridMath.geometry(1000f, 500f, 4, 2, gap)
        // tile = 245, grid width = 4*245 + 30 = 1010 > 1000, so width limits: (1000-30)/4 = 242.5
        val cell0 = g.cellAt(g.cellLeft(0) + 5, g.cellTop(0) + 5)
        assertEquals(0, cell0)
        assertEquals(1, g.cellAt(g.cellLeft(1) + 100, g.cellTop(0) + 100))
        assertEquals(7, g.cellAt(g.cellLeft(3) + 100, g.cellTop(1) + 100))
        // the gap between column 0 and column 1 belongs to a neighbour, not to nowhere
        val inGap = g.cellAt(g.cellLeft(0) + g.tile + gap / 2 - 1, g.cellTop(0) + 10)
        assertEquals(0, inGap)
        assertEquals(1, g.cellAt(g.cellLeft(0) + g.tile + gap / 2 + 1, g.cellTop(0) + 10))
    }

    @Test
    fun `points outside the grid hit nothing`() {
        val g = GridMath.geometry(1000f, 500f, 4, 2, gap)
        assertNull(g.cellAt(-20f, 100f))
        assertNull(g.cellAt(100f, -20f))
        assertNull(g.cellAt(g.left + g.width + gap * 2, 100f))
        assertNull(g.cellAt(100f, g.top + g.height + gap * 2))
        assertNull(GridGeometry(1, 1, 0f, 0f, 0f, 0f).cellAt(0f, 0f))
    }

    @Test
    fun `cell indices run row by row`() {
        val g = GridMath.geometry(1000f, 600f, 3, 2, gap)
        val indices =
            (0 until g.rows).flatMap { row ->
                (0 until g.columns).map { column -> g.cellAt(g.cellLeft(column) + 1, g.cellTop(row) + 1) }
            }
        assertEquals((0 until 6).toList(), indices)
        assertEquals(6, g.capacity)
    }
}
