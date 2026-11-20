package io.github.ratmux.sdhelper.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/** What the editor wants to know about touches on a page. Positions are in the page's own pixel space. */
interface GridGestureHandler {
    /** A short tap on a cell (empty or not). */
    fun onTap(index: Int)

    /** A long press on a cell started a drag. */
    fun onDragStart(
        index: Int,
        position: Offset,
    )

    fun onDrag(position: Offset)

    fun onDragEnd()

    fun onDragCancel()
}

/**
 * Tap and long-press-then-drag on a page of cells, for the editor.
 *
 * The drag part reads moves in the `Initial` pass and consumes them once the long press has happened. That pass runs
 * from parent to child, so the pager around the page never sees these moves and does not scroll while a button is
 * being dragged. Before the long press nothing is consumed, so swiping to change page still works.
 */
@Composable
fun Modifier.gridGestures(
    enabled: Boolean,
    geometry: () -> GridGeometry?,
    handler: GridGestureHandler,
): Modifier {
    val currentGeometry by rememberUpdatedState(geometry)
    val currentHandler by rememberUpdatedState(handler)
    if (!enabled) return this
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { position ->
                    currentGeometry()?.cellAt(position.x, position.y)?.let { currentHandler.onTap(it) }
                },
            )
        }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                val cell =
                    currentGeometry()?.cellAt(longPress.position.x, longPress.position.y) ?: return@awaitEachGesture
                currentHandler.onDragStart(cell, longPress.position)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        currentHandler.onDragCancel()
                        return@awaitEachGesture
                    }
                    if (!change.pressed) {
                        change.consume()
                        currentHandler.onDragEnd()
                        return@awaitEachGesture
                    }
                    if (change.positionChanged()) currentHandler.onDrag(change.position)
                    change.consume()
                }
            }
        }
}
