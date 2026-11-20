package io.github.ratmux.sdhelper.ui.components

/**
 * While a button is dragged, holding it near the left or right edge turns the page. This is that rule as a small
 * state machine fed with the pointer position and the time, so it can be tested without a screen.
 */
class EdgeDwell(
    private val dwellMillis: Long = DEFAULT_DWELL_MILLIS,
    private val edgeFraction: Float = DEFAULT_EDGE_FRACTION,
) {
    enum class Direction { PREVIOUS, NEXT }

    private var side: Direction? = null
    private var since = 0L

    /**
     * Returns a direction once the pointer has stayed in the same edge zone for the dwell time, and then again after
     * every further dwell time (so a held button keeps paging). Anywhere else it returns `null` and starts over.
     */
    fun update(
        x: Float,
        width: Float,
        nowMillis: Long,
    ): Direction? {
        val zone =
            when {
                width <= 0f -> null
                x < width * edgeFraction -> Direction.PREVIOUS
                x > width * (1f - edgeFraction) -> Direction.NEXT
                else -> null
            }
        if (zone == null) {
            side = null
            return null
        }
        if (zone != side) {
            side = zone
            since = nowMillis
            return null
        }
        if (nowMillis - since >= dwellMillis) {
            since = nowMillis
            return zone
        }
        return null
    }

    fun reset() {
        side = null
    }

    companion object {
        const val DEFAULT_DWELL_MILLIS = 700L
        const val DEFAULT_EDGE_FRACTION = 0.12f
    }
}
