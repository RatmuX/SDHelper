package io.github.ratmux.sdhelper.core.model

import java.util.UUID

/** Number of button columns and rows of a profile. Every page of a profile uses the same grid. */
data class GridSize(
    val columns: Int,
    val rows: Int,
) {
    val capacity: Int get() = columns * rows

    companion object {
        /** The sizes offered in the editor; large square buttons fit all of them on a landscape phone. */
        val ALLOWED = listOf(GridSize(3, 2), GridSize(4, 2), GridSize(5, 2), GridSize(4, 3), GridSize(6, 3))
        val DEFAULT = GridSize(4, 2)
    }
}

/** What a grid cell holds: a button that runs an action, or a folder that opens a grid of such buttons. */
sealed interface DeckSlot {
    data class Action(
        /** `providerId/actionId` as published by the computer. */
        val actionId: String,
    ) : DeckSlot

    /**
     * A button that opens a grid of action buttons. Folders hold only actions (they do not nest) and are not tied
     * to the grid: [actions] is a plain ordered list shown row by row in the profile's grid, on as many swipeable
     * pages as needed, so changing the grid size never has to move anything inside a folder.
     */
    data class Folder(
        val id: String,
        val name: String,
        val actions: List<String> = emptyList(),
    ) : DeckSlot {
        /** The folder's buttons cut into pages of [capacity]; always at least one (possibly empty) page. */
        fun pages(capacity: Int): List<List<String>> =
            if (capacity <= 0 || actions.isEmpty()) listOf(emptyList()) else actions.chunked(capacity)
    }
}

/** One swipeable page. [slots] has exactly one entry per grid cell, row by row; `null` is an empty cell. */
data class DeckPage(
    val id: String,
    val name: String,
    val slots: List<DeckSlot?>,
)

data class DeckProfile(
    val id: String,
    val name: String,
    val grid: GridSize,
    val pages: List<DeckPage>,
) {
    init {
        require(grid in GridSize.ALLOWED) { "Unsupported grid $grid" }
        require(pages.size in 1..LayoutLimits.MAX_PAGES) { "A profile has 1..${LayoutLimits.MAX_PAGES} pages" }
        require(pages.all { it.slots.size == grid.capacity }) { "Every page must have one slot per grid cell" }
    }

    fun slotAt(position: SlotPosition): DeckSlot? = pages.getOrNull(position.page)?.slots?.getOrNull(position.index)

    fun contains(position: SlotPosition): Boolean =
        position.index in 0 until grid.capacity && position.page in pages.indices

    /** Every action id the layout uses, also inside folders. */
    val actionIds: Set<String>
        get() =
            pages
                .flatMap { it.slots }
                .flatMap { slot ->
                    when (slot) {
                        is DeckSlot.Action -> listOf(slot.actionId)
                        is DeckSlot.Folder -> slot.actions
                        null -> emptyList()
                    }
                }.toSet()

    /** Number of cells that hold something (a folder counts as one) on all pages. */
    val buttonCount: Int get() = pages.sumOf { page -> page.slots.count { it != null } }
}

/** A cell: page number and index within the page's slot list (row-major). */
data class SlotPosition(
    val page: Int,
    val index: Int,
)

object LayoutLimits {
    const val MAX_PAGES = 8
    const val MAX_NAME_LENGTH = 24

    /** Buttons in one folder; at most a few pages of the largest grid. */
    const val MAX_FOLDER_ACTIONS = 36
}

object LayoutIds {
    fun random(): String = UUID.randomUUID().toString()
}
