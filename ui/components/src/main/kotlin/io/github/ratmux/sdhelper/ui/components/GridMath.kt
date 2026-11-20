package io.github.ratmux.sdhelper.ui.components

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where the square buttons of a page are, in pixels, inside the area given to the page. Pure arithmetic so it can be
 * tested without a screen; the same geometry drives drawing and touch hit-testing.
 */
data class GridGeometry(
    val columns: Int,
    val rows: Int,
    /** Edge length of one square button. */
    val tile: Float,
    val gap: Float,
    /** Left and top edge of the whole grid, which is centred in the available area. */
    val left: Float,
    val top: Float,
) {
    val width: Float get() = columns * tile + (columns - 1) * gap
    val height: Float get() = rows * tile + (rows - 1) * gap
    val capacity: Int get() = columns * rows

    fun cellLeft(column: Int): Float = left + column * (tile + gap)

    fun cellTop(row: Int): Float = top + row * (tile + gap)

    /**
     * The cell under a point. A cell owns its button plus half of the gap around it, so a finger that lands between two
     * buttons still picks one; points outside the grid give `null`.
     */
    fun cellAt(
        x: Float,
        y: Float,
    ): Int? {
        val pitch = tile + gap
        if (tile <= 0f) return null
        val relativeX = x - left + gap / 2
        val relativeY = y - top + gap / 2
        if (relativeX < 0f || relativeY < 0f) return null
        val column = floor(relativeX / pitch).toInt()
        val row = floor(relativeY / pitch).toInt()
        return if (column in 0 until columns && row in 0 until rows) row * columns + column else null
    }
}

object GridMath {
    /** The largest grid of equal squares that fits [availableWidth] x [availableHeight], centred. */
    fun geometry(
        availableWidth: Float,
        availableHeight: Float,
        columns: Int,
        rows: Int,
        gap: Float,
    ): GridGeometry {
        require(columns > 0 && rows > 0) { "A grid needs at least one cell" }
        val byWidth = (availableWidth - (columns - 1) * gap) / columns
        val byHeight = (availableHeight - (rows - 1) * gap) / rows
        val tile = max(0f, min(byWidth, byHeight))
        val width = columns * tile + (columns - 1) * gap
        val height = rows * tile + (rows - 1) * gap
        return GridGeometry(columns, rows, tile, gap, (availableWidth - width) / 2, (availableHeight - height) / 2)
    }
}
