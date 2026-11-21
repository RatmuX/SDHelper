package io.github.ratmux.sdhelper.core.model

/** Result of changing the grid size: the new profile and how many buttons had to move to a different cell. */
data class ResizeResult(
    val profile: DeckProfile,
    val movedButtons: Int,
)

/**
 * All edits of a layout as pure functions: they return a new profile and never throw for bad positions or limits,
 * they return the profile unchanged. Nothing here ever deletes a button except [clear] and [removePage] (which the
 * editor confirms), so a resize cannot lose buttons silently.
 */
object LayoutOps {
    fun setSlot(
        profile: DeckProfile,
        at: SlotPosition,
        slot: DeckSlot?,
    ): DeckProfile {
        if (!profile.contains(at)) return profile
        return profile.replacePage(at.page) { page ->
            page.copy(slots = page.slots.toMutableList().also { it[at.index] = slot })
        }
    }

    fun clear(
        profile: DeckProfile,
        at: SlotPosition,
    ): DeckProfile = setSlot(profile, at, null)

    /**
     * Moves the button in [from] to [to], also across pages. If [to] holds a button the two swap places.
     * Moving an empty cell or onto itself changes nothing.
     */
    fun move(
        profile: DeckProfile,
        from: SlotPosition,
        to: SlotPosition,
    ): DeckProfile {
        if (from == to || !profile.contains(from) || !profile.contains(to)) return profile
        val moving = profile.slotAt(from) ?: return profile
        val displaced = profile.slotAt(to)
        return setSlot(setSlot(profile, to, moving), from, displaced)
    }

    fun canAddPage(profile: DeckProfile): Boolean = profile.pages.size < LayoutLimits.MAX_PAGES

    fun addPage(
        profile: DeckProfile,
        name: String,
        id: String = LayoutIds.random(),
    ): DeckProfile {
        if (!canAddPage(profile)) return profile
        val page = DeckPage(id, cleanName(name) ?: "Page ${profile.pages.size + 1}", emptySlots(profile.grid))
        return profile.copy(pages = profile.pages + page)
    }

    /** The last remaining page cannot be removed. */
    fun removePage(
        profile: DeckProfile,
        index: Int,
    ): DeckProfile {
        if (profile.pages.size <= 1 || index !in profile.pages.indices) return profile
        return profile.copy(pages = profile.pages.filterIndexed { i, _ -> i != index })
    }

    fun renamePage(
        profile: DeckProfile,
        index: Int,
        name: String,
    ): DeckProfile {
        val clean = cleanName(name) ?: return profile
        if (index !in profile.pages.indices) return profile
        return profile.replacePage(index) { it.copy(name = clean) }
    }

    fun reorderPage(
        profile: DeckProfile,
        from: Int,
        to: Int,
    ): DeckProfile {
        if (from !in profile.pages.indices || to !in profile.pages.indices || from == to) return profile
        val pages = profile.pages.toMutableList()
        pages.add(to, pages.removeAt(from))
        return profile.copy(pages = pages)
    }

    fun renameProfile(
        profile: DeckProfile,
        name: String,
    ): DeckProfile = cleanName(name)?.let { profile.copy(name = it) } ?: profile

    /**
     * Changes the grid size. Buttons keep their row and column where the new grid still has that cell. Those that do
     * not fit go to the first free cell of the same page, then of the other pages, then to new pages. Returns `null`
     * (and changes nothing) if they would not fit within [LayoutLimits.MAX_PAGES]: no button is ever dropped.
     */
    fun resizeGrid(
        profile: DeckProfile,
        newGrid: GridSize,
        newId: () -> String = LayoutIds::random,
        overflowPageName: (Int) -> String = { "Page $it" },
    ): ResizeResult? {
        if (newGrid !in GridSize.ALLOWED) return null
        if (newGrid == profile.grid) return ResizeResult(profile, 0)

        val old = profile.grid
        val overflow = mutableListOf<Pair<Int, DeckSlot>>()
        val pages =
            profile.pages
                .mapIndexed { pageIndex, page ->
                    val slots = MutableList<DeckSlot?>(newGrid.capacity) { null }
                    page.slots.forEachIndexed { index, slot ->
                        if (slot == null) return@forEachIndexed
                        val row = index / old.columns
                        val column = index % old.columns
                        if (row < newGrid.rows && column < newGrid.columns) {
                            slots[row * newGrid.columns + column] = slot
                        } else {
                            overflow += pageIndex to slot
                        }
                    }
                    page.copy(slots = slots)
                }.toMutableList()

        for ((fromPage, slot) in overflow) {
            val searchOrder = (fromPage until pages.size) + (0 until fromPage)
            val target = searchOrder.firstOrNull { pages[it].slots.contains(null) }
            if (target != null) {
                val slots = pages[target].slots.toMutableList()
                slots[slots.indexOf(null)] = slot
                pages[target] = pages[target].copy(slots = slots)
            } else {
                if (pages.size >= LayoutLimits.MAX_PAGES) return null
                val slots = MutableList<DeckSlot?>(newGrid.capacity) { null }
                slots[0] = slot
                pages += DeckPage(newId(), overflowPageName(pages.size + 1), slots)
            }
        }
        return ResizeResult(profile.copy(grid = newGrid, pages = pages), overflow.size)
    }

    fun emptySlots(grid: GridSize): List<DeckSlot?> = List(grid.capacity) { null }

    /** Trimmed and limited to [LayoutLimits.MAX_NAME_LENGTH] characters; `null` if nothing is left. */
    fun cleanName(raw: String): String? =
        raw
            .filter { !it.isISOControl() }
            .trim()
            .take(LayoutLimits.MAX_NAME_LENGTH)
            .trim()
            .ifEmpty { null }

    private fun DeckProfile.replacePage(
        index: Int,
        transform: (DeckPage) -> DeckPage,
    ): DeckProfile = copy(pages = pages.mapIndexed { i, page -> if (i == index) transform(page) else page })
}
