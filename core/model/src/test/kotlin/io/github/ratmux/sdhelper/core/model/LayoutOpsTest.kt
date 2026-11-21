package io.github.ratmux.sdhelper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutOpsTest {
    private fun action(name: String) = DeckSlot.Action("p/$name")

    private fun profile(
        grid: GridSize = GridSize(4, 2),
        pages: Int = 2,
    ) = DeckProfile("pr", "Test", grid, List(pages) { DeckPage("pg$it", "Page $it", LayoutOps.emptySlots(grid)) })

    private fun DeckProfile.with(
        page: Int,
        index: Int,
        name: String,
    ) = LayoutOps.setSlot(this, SlotPosition(page, index), action(name))

    private fun DeckProfile.actionIds() =
        pages
            .flatMap {
                it.slots
            }.filterNotNull()
            .map { (it as DeckSlot.Action).actionId }
            .sorted()

    @Test
    fun `a profile must have a supported grid, 1 to 8 pages and one slot per cell`() {
        assertFailsWith<IllegalArgumentException> { profile(grid = GridSize(7, 7)) }
        assertFailsWith<IllegalArgumentException> { profile(pages = 0) }
        assertFailsWith<IllegalArgumentException> { profile(pages = 9) }
        assertFailsWith<IllegalArgumentException> {
            DeckProfile("x", "x", GridSize(4, 2), listOf(DeckPage("p", "p", List(3) { null })))
        }
    }

    @Test
    fun `set and clear change only the addressed cell and ignore bad positions`() {
        val start = profile()
        val placed = start.with(1, 3, "a")
        assertEquals(action("a"), placed.slotAt(SlotPosition(1, 3)))
        assertEquals(1, placed.buttonCount)
        assertEquals(0, LayoutOps.clear(placed, SlotPosition(1, 3)).buttonCount)

        assertSame(start, LayoutOps.setSlot(start, SlotPosition(5, 0), action("a")))
        assertSame(start, LayoutOps.setSlot(start, SlotPosition(0, 8), action("a")))
        assertSame(start, LayoutOps.setSlot(start, SlotPosition(0, -1), action("a")))
    }

    @Test
    fun `moving to an empty cell relocates the button, also across pages`() {
        val start = profile().with(0, 0, "a")
        val moved = LayoutOps.move(start, SlotPosition(0, 0), SlotPosition(1, 5))
        assertNull(moved.slotAt(SlotPosition(0, 0)))
        assertEquals(action("a"), moved.slotAt(SlotPosition(1, 5)))
    }

    @Test
    fun `moving onto a button swaps the two`() {
        val start = profile().with(0, 0, "a").with(1, 2, "b")
        val moved = LayoutOps.move(start, SlotPosition(0, 0), SlotPosition(1, 2))
        assertEquals(action("b"), moved.slotAt(SlotPosition(0, 0)))
        assertEquals(action("a"), moved.slotAt(SlotPosition(1, 2)))
        assertEquals(start.actionIds(), moved.actionIds(), "swapping loses nothing")
    }

    @Test
    fun `moving an empty cell, onto itself or out of range changes nothing`() {
        val start = profile().with(0, 0, "a")
        assertSame(start, LayoutOps.move(start, SlotPosition(0, 1), SlotPosition(0, 2)))
        assertSame(start, LayoutOps.move(start, SlotPosition(0, 0), SlotPosition(0, 0)))
        assertSame(start, LayoutOps.move(start, SlotPosition(0, 0), SlotPosition(0, 99)))
        assertSame(start, LayoutOps.move(start, SlotPosition(9, 0), SlotPosition(0, 1)))
    }

    @Test
    fun `pages can be added up to the limit and the last page cannot be removed`() {
        var p = profile(pages = 1)
        repeat(LayoutLimits.MAX_PAGES - 1) {
            assertTrue(LayoutOps.canAddPage(p))
            p = LayoutOps.addPage(p, "")
        }
        assertEquals(LayoutLimits.MAX_PAGES, p.pages.size)
        assertEquals("Page 2", p.pages[1].name, "blank names get a default")
        assertEquals(false, LayoutOps.canAddPage(p))
        assertSame(p, LayoutOps.addPage(p, "one too many"))

        repeat(LayoutLimits.MAX_PAGES - 1) { p = LayoutOps.removePage(p, 0) }
        assertEquals(1, p.pages.size)
        assertSame(p, LayoutOps.removePage(p, 0), "the last page stays")
    }

    @Test
    fun `new pages have the profile's grid size and are empty`() {
        val p = LayoutOps.addPage(profile(GridSize(6, 3)), "Wide")
        assertEquals(
            18,
            p.pages
                .last()
                .slots.size,
        )
        assertEquals(
            0,
            p.pages
                .last()
                .slots
                .count { it != null },
        )
    }

    @Test
    fun `renaming cleans names and ignores empty ones`() {
        val p = profile()
        assertEquals("Games", LayoutOps.renamePage(p, 0, "  Games\n").pages[0].name)
        assertEquals("x".repeat(LayoutLimits.MAX_NAME_LENGTH), LayoutOps.renamePage(p, 0, "x".repeat(99)).pages[0].name)
        assertSame(p, LayoutOps.renamePage(p, 0, "   "))
        assertSame(p, LayoutOps.renamePage(p, 7, "x"))
        assertEquals("New", LayoutOps.renameProfile(p, "New").name)
        assertSame(p, LayoutOps.renameProfile(p, ""))
    }

    @Test
    fun `pages can be reordered`() {
        val p = profile(pages = 3)
        assertEquals(listOf("pg1", "pg2", "pg0"), LayoutOps.reorderPage(p, 0, 2).pages.map { it.id })
        assertEquals(listOf("pg2", "pg0", "pg1"), LayoutOps.reorderPage(p, 2, 0).pages.map { it.id })
        assertSame(p, LayoutOps.reorderPage(p, 1, 1))
        assertSame(p, LayoutOps.reorderPage(p, 0, 5))
    }

    @Test
    fun `growing the grid keeps every button at the same row and column`() {
        // 4x2: a at row 0 col 3, b at row 1 col 0
        val start = profile().with(0, 3, "a").with(0, 4, "b")
        val grown = assertNotNull(LayoutOps.resizeGrid(start, GridSize(6, 3))).profile

        assertEquals(GridSize(6, 3), grown.grid)
        assertEquals(action("a"), grown.slotAt(SlotPosition(0, 0 * 6 + 3)))
        assertEquals(action("b"), grown.slotAt(SlotPosition(0, 1 * 6 + 0)))
        assertEquals(0, LayoutOps.resizeGrid(start, GridSize(6, 3))!!.movedButtons)
    }

    @Test
    fun `shrinking keeps what fits and moves the rest into free cells without losing any`() {
        // 4x2 -> 3x2: the button at column 3 no longer has a cell
        val start = profile(pages = 1).with(0, 0, "a").with(0, 3, "b").with(0, 7, "c")
        val result = assertNotNull(LayoutOps.resizeGrid(start, GridSize(3, 2)))

        assertEquals(2, result.movedButtons)
        assertEquals(start.actionIds(), result.profile.actionIds())
        assertEquals(action("a"), result.profile.slotAt(SlotPosition(0, 0)), "a stays where it was")
        assertEquals(1, result.profile.pages.size, "they fit on the same page")
    }

    @Test
    fun `overflow goes to other pages and finally to new pages`() {
        var full = profile(GridSize(6, 3), pages = 1)
        repeat(18) { full = full.with(0, it, "b$it") }
        val result = assertNotNull(LayoutOps.resizeGrid(full, GridSize(3, 2)) { "n" }).profile

        assertEquals(18, result.buttonCount)
        assertEquals(3, result.pages.size)
        assertTrue(result.pages.all { it.slots.size == 6 })
    }

    @Test
    fun `a resize that would not fit in the allowed pages is refused and nothing changes`() {
        var full = profile(GridSize(6, 3), pages = LayoutLimits.MAX_PAGES)
        for (page in 0 until LayoutLimits.MAX_PAGES) repeat(18) { full = full.with(page, it, "b$page-$it") }

        assertNull(LayoutOps.resizeGrid(full, GridSize(3, 2)))
        assertEquals(144, full.buttonCount)
    }

    @Test
    fun `resizing between every pair of grids never loses a button when it succeeds`() {
        for (from in GridSize.ALLOWED) {
            for (to in GridSize.ALLOWED) {
                // two pages, every third cell filled
                var p = profile(from, pages = 2)
                for (page in 0..1) for (i in 0 until from.capacity step 3) p = p.with(page, i, "b$page-$i")
                val result = LayoutOps.resizeGrid(p, to)
                assertNotNull(result, "$from -> $to")
                assertEquals(p.actionIds(), result.profile.actionIds(), "$from -> $to")
                assertTrue(result.profile.pages.all { it.slots.size == to.capacity }, "$from -> $to")
                assertTrue(result.profile.pages.size <= LayoutLimits.MAX_PAGES)
            }
        }
    }

    @Test
    fun `unsupported grids are refused and the same grid is a no-op`() {
        val p = profile()
        assertNull(LayoutOps.resizeGrid(p, GridSize(7, 7)))
        val same = assertNotNull(LayoutOps.resizeGrid(p, p.grid))
        assertSame(p, same.profile)
        assertEquals(0, same.movedButtons)
    }
}
