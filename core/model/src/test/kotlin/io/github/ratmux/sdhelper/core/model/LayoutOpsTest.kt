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

class LayoutFolderOpsTest {
    private val grid = GridSize(4, 2)
    private val home = SlotPosition(0, 0)

    private fun profile(pages: Int = 2) =
        DeckProfile("pr", "Test", grid, List(pages) { DeckPage("pg$it", "Page $it", LayoutOps.emptySlots(grid)) })

    private fun withFolder(vararg actions: String): DeckProfile {
        var p = LayoutOps.createFolder(profile(), home, "Games", id = "f1")
        actions.forEach { p = LayoutOps.addToFolder(p, home, it) }
        return p
    }

    private fun DeckProfile.folder() = slotAt(home) as DeckSlot.Folder

    @Test
    fun `a folder can only be created in an empty cell and gets a clean name`() {
        val p = LayoutOps.createFolder(profile(), home, "  Streaming  ", id = "f")
        assertEquals(DeckSlot.Folder("f", "Streaming"), p.slotAt(home))
        assertSame(p, LayoutOps.createFolder(p, home, "Other"), "occupied cell: unchanged")
        assertEquals("Folder", LayoutOps.createFolder(profile(), home, "   ").folder().name)
        val outside = profile()
        assertSame(outside, LayoutOps.createFolder(outside, SlotPosition(5, 0), "x"))
    }

    @Test
    fun `buttons are added, replaced, removed and reordered inside a folder`() {
        var p = withFolder("a", "b", "c")
        assertEquals(listOf("a", "b", "c"), p.folder().actions)
        p = LayoutOps.replaceInFolder(p, home, 1, "x")
        p = LayoutOps.removeFromFolder(p, home, 0)
        assertEquals(listOf("x", "c"), p.folder().actions)
        p = LayoutOps.moveInFolder(p, home, 0, 1)
        assertEquals(listOf("c", "x"), p.folder().actions)
        assertSame(p, LayoutOps.moveInFolder(p, home, 0, 9))
        assertSame(p, LayoutOps.removeFromFolder(p, home, 9))
        assertSame(p, LayoutOps.replaceInFolder(p, home, 9, "z"))
    }

    @Test
    fun `operations on a cell that is not a folder change nothing`() {
        val p = LayoutOps.setSlot(profile(), home, DeckSlot.Action("a"))
        assertSame(p, LayoutOps.addToFolder(p, home, "b"))
        assertSame(p, LayoutOps.renameFolder(p, home, "n"))
        assertSame(p, LayoutOps.moveOutOfFolder(p, home, 0))
    }

    @Test
    fun `a folder holds at most the limit`() {
        var p = LayoutOps.createFolder(profile(), home, "Big", id = "f")
        repeat(LayoutLimits.MAX_FOLDER_ACTIONS + 5) { p = LayoutOps.addToFolder(p, home, "a$it") }
        assertEquals(LayoutLimits.MAX_FOLDER_ACTIONS, p.folder().actions.size)
    }

    @Test
    fun `dropping a button on a folder puts it inside, a full folder refuses it`() {
        var p = withFolder("a")
        p = LayoutOps.setSlot(p, SlotPosition(0, 3), DeckSlot.Action("b"))
        p = LayoutOps.move(p, SlotPosition(0, 3), home)
        assertEquals(listOf("a", "b"), p.folder().actions)
        assertNull(p.slotAt(SlotPosition(0, 3)))

        var full = LayoutOps.createFolder(profile(), home, "Full", id = "f")
        repeat(LayoutLimits.MAX_FOLDER_ACTIONS) { full = LayoutOps.addToFolder(full, home, "a$it") }
        full = LayoutOps.setSlot(full, SlotPosition(0, 3), DeckSlot.Action("extra"))
        assertSame(full, LayoutOps.move(full, SlotPosition(0, 3), home))
    }

    @Test
    fun `a folder moves and swaps like any button`() {
        val p = withFolder("a")
        val moved = LayoutOps.move(p, home, SlotPosition(1, 5))
        assertEquals("f1", (moved.slotAt(SlotPosition(1, 5)) as DeckSlot.Folder).id)
        assertNull(moved.slotAt(home))
    }

    @Test
    fun `taking a button out puts it into the first free cell, own page first`() {
        var p = withFolder("a", "b")
        p = LayoutOps.moveOutOfFolder(p, home, 0)
        assertEquals(listOf("b"), p.folder().actions)
        assertEquals(DeckSlot.Action("a"), p.slotAt(SlotPosition(0, 1)))

        // own page full: another page takes it, and when no page has room nothing is lost
        var crowded = withFolder("a")
        for (page in 0..1) {
            for (i in 0 until grid.capacity) {
                if (crowded.slotAt(SlotPosition(page, i)) ==
                    null
                ) {
                    crowded = LayoutOps.setSlot(crowded, SlotPosition(page, i), DeckSlot.Action("p$page$i"))
                }
            }
        }
        assertSame(crowded, LayoutOps.moveOutOfFolder(crowded, home, 0))
        val room = LayoutOps.clear(crowded, SlotPosition(1, 2))
        assertEquals(DeckSlot.Action("a"), LayoutOps.moveOutOfFolder(room, home, 0).slotAt(SlotPosition(1, 2)))
    }

    @Test
    fun `resizing the grid keeps folders and their buttons`() {
        val p = withFolder("a", "b", "c")
        for (target in GridSize.ALLOWED) {
            val result = LayoutOps.resizeGrid(p, target)
            assertNotNull(result)
            assertEquals(setOf("a", "b", "c"), result.profile.actionIds)
        }
    }

    @Test
    fun `folder pages follow the grid capacity and an empty folder has one empty page`() {
        val folder = DeckSlot.Folder("f", "F", List(10) { "a$it" })
        assertEquals(listOf(8, 2), folder.pages(8).map { it.size })
        assertEquals(listOf(10), folder.pages(18).map { it.size })
        assertEquals(listOf(emptyList()), DeckSlot.Folder("f", "F").pages(8))
        assertEquals(listOf(emptyList()), folder.pages(0))
    }

    @Test
    fun `the action ids of a layout include those inside folders`() {
        var p = withFolder("a", "b")
        p = LayoutOps.setSlot(p, SlotPosition(1, 0), DeckSlot.Action("c"))
        assertEquals(setOf("a", "b", "c"), p.actionIds)
        assertEquals(2, p.buttonCount, "the folder is one cell, the action another")
    }
}
