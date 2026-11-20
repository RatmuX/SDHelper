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

/**
 * What a grid cell holds. Only actions exist today; a `Folder` (a button that opens a sub-page) is planned and
 * the stored form already carries a type discriminator, so adding it does not break saved layouts.
 */
sealed interface DeckSlot {
    data class Action(
        /** `providerId/actionId` as published by the computer. */
        val actionId: String,
    ) : DeckSlot
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

    /** Number of buttons placed on all pages. */
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
}

object LayoutIds {
    fun random(): String = UUID.randomUUID().toString()
}
