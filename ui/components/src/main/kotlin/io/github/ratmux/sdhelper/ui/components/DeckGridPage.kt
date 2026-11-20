package io.github.ratmux.sdhelper.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Spacing between buttons. Generous enough for a thumb, small enough to keep the buttons large. */
val DeckGridGap: Dp = 12.dp

/**
 * One page of square buttons: the largest equal squares that fit the space given, centred, with [cell] drawing
 * the content of each cell. [onGeometry] reports where the cells are, so gestures can use the same numbers.
 * Every page of a profile gets the same space and grid size, so swiping never changes a button's size or place.
 */
@Composable
fun DeckGridPage(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    gap: Dp = DeckGridGap,
    onGeometry: (GridGeometry) -> Unit = {},
    cell: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val geometry =
            GridMath.geometry(
                availableWidth = constraints.maxWidth.toFloat(),
                availableHeight = constraints.maxHeight.toFloat(),
                columns = columns,
                rows = rows,
                gap = with(density) { gap.toPx() },
            )
        SideEffect { onGeometry(geometry) }
        val tileSize = with(density) { geometry.tile.toDp() }
        for (index in 0 until geometry.capacity) {
            val row = index / columns
            val column = index % columns
            Box(
                Modifier
                    .offset { IntOffset(geometry.cellLeft(column).roundToInt(), geometry.cellTop(row).roundToInt()) }
                    .size(tileSize),
            ) {
                cell(index, Modifier.fillMaxSize())
            }
        }
    }
}
