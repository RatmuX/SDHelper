package io.github.ratmux.sdhelper.feature.deckeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.SlotPosition
import io.github.ratmux.sdhelper.ui.components.DeckGridPage
import io.github.ratmux.sdhelper.ui.components.GridGeometry
import io.github.ratmux.sdhelper.ui.components.GridGestureHandler
import io.github.ratmux.sdhelper.ui.components.gridGestures
import io.github.ratmux.sdhelper.ui.designapi.DeckButton
import io.github.ratmux.sdhelper.ui.designapi.DeckButtonTile
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckEmptyTile
import io.github.ratmux.sdhelper.ui.designapi.DeckListItem
import io.github.ratmux.sdhelper.ui.designapi.DeckModal
import io.github.ratmux.sdhelper.ui.designapi.DeckPageIndicator
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckText
import io.github.ratmux.sdhelper.ui.designapi.DeckTextField
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import kotlin.math.roundToInt

/** A button of the folder being dragged to a new place in the folder's order. */
private data class FolderDrag(
    val index: Int,
    val pointer: Offset,
)

private sealed interface FolderDialog {
    /** Menu of the button at [index]. */
    data class Item(
        val index: Int,
    ) : FolderDialog

    /** Pick an action: replaces the button at [index], or appends one when [index] is null. */
    data class Picker(
        val index: Int?,
    ) : FolderDialog

    data object Rename : FolderDialog
}

/**
 * The contents of one folder: its buttons laid out like the control panel shows them (the layout's grid, as many
 * pages as needed) plus one empty cell to add a button. Tap a button for its menu, press and hold to drag it to
 * another place in the order (within the page shown).
 */
@Composable
fun FolderEditor(
    state: EditorUiState,
    profile: DeckProfile,
    at: SlotPosition,
    folder: DeckSlot.Folder,
    viewModel: EditorViewModel,
    onBack: () -> Unit,
) {
    val capacity = profile.grid.capacity
    // one extra cell at the end to add a button, unless the folder is full
    val cells =
        folder.actions.map<String, String?> { it } +
            if (folder.actions.size < LayoutLimits.MAX_FOLDER_ACTIONS) listOf(null) else emptyList()
    val pages = cells.chunked(capacity).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState { pages.size }
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    var drag by remember { mutableStateOf<FolderDrag?>(null) }
    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    val latest by rememberUpdatedState(folder)

    val handler =
        remember(pager) {
            object : GridGestureHandler {
                override fun onTap(index: Int) {
                    val global = pager.currentPage * capacity + index
                    if (global in latest.actions.indices) dialog = FolderDialog.Item(global)
                    if (global == latest.actions.size) dialog = FolderDialog.Picker(null)
                }

                override fun onDragStart(
                    index: Int,
                    position: Offset,
                ) {
                    val global = pager.currentPage * capacity + index
                    if (global in latest.actions.indices) drag = FolderDrag(global, position)
                }

                override fun onDrag(position: Offset) {
                    drag = drag?.copy(pointer = position)
                }

                override fun onDragEnd() {
                    val finished = drag
                    drag = null
                    val target = finished?.let { geometry?.cellAt(it.pointer.x, it.pointer.y) }
                    if (finished != null && target != null) {
                        val to = (pager.currentPage * capacity + target).coerceAtMost(latest.actions.lastIndex)
                        viewModel.moveInFolder(at, finished.index, to)
                    }
                }

                override fun onDragCancel() {
                    drag = null
                }
            }
        }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckButton(stringResource(R.string.editor_folder_back), onBack, emphasis = DeckEmphasis.SECONDARY)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val notice = state.message
                if (notice == EditorMessage.NO_FREE_CELL || notice == EditorMessage.FOLDER_FULL) {
                    DeckStatusMessage(
                        stringResource(
                            if (notice ==
                                EditorMessage.FOLDER_FULL
                            ) {
                                R.string.editor_message_folder_full
                            } else {
                                R.string.editor_message_no_free_cell
                            },
                        ),
                        tone = DeckTone.ERROR,
                    )
                } else {
                    DeckText(folder.name, style = DeckTextStyle.TITLE)
                    DeckText(stringResource(R.string.editor_folder_hint), style = DeckTextStyle.CAPTION)
                }
            }
            DeckButton(stringResource(R.string.editor_folder_rename), {
                dialog = FolderDialog.Rename
            }, emphasis = DeckEmphasis.SECONDARY)
            if (pages.size > 1) DeckPageIndicator(count = pages.size, current = pager.currentPage)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().gridGestures(enabled = true, geometry = { geometry }, handler = handler),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = drag == null,
            ) { pageIndex ->
                DeckGridPage(profile.grid.columns, profile.grid.rows, onGeometry = { geometry = it }) { index, cell ->
                    val global = pageIndex * capacity + index
                    val hovered =
                        drag?.let { d ->
                            pageIndex == pager.currentPage && geometry?.cellAt(d.pointer.x, d.pointer.y) == index
                        } == true
                    val actionId = pages.getOrNull(pageIndex)?.getOrNull(index)
                    when {
                        actionId != null -> {
                            val button = state.buttons[actionId]
                            DeckButtonTile(
                                title = button?.title ?: actionId,
                                modifier = cell,
                                iconKey = button?.iconKey,
                                enabled = drag?.index != global,
                                highlighted = hovered,
                            )
                        }

                        global <= folder.actions.size && global < LayoutLimits.MAX_FOLDER_ACTIONS -> {
                            DeckEmptyTile(modifier = cell, highlighted = hovered)
                        }

                        else -> {
                            // beyond the last cell: nothing to show
                        }
                    }
                }
            }
            drag?.let { d ->
                val g = geometry
                val button = state.buttons[folder.actions.getOrNull(d.index)]
                if (g != null && button != null) {
                    val tile = with(LocalDensity.current) { g.tile.toDp() }
                    DeckButtonTile(
                        title = button.title,
                        modifier =
                            Modifier
                                .zIndex(1f)
                                .offset {
                                    IntOffset(
                                        (d.pointer.x - g.tile / 2).roundToInt(),
                                        (d.pointer.y - g.tile / 2).roundToInt(),
                                    )
                                }.size(tile)
                                .alpha(DRAG_ALPHA),
                        iconKey = button.iconKey,
                        highlighted = true,
                    )
                }
            }
        }
    }

    FolderDialogs(state, folder, at, dialog, { dialog = it }, viewModel)
}

private const val DRAG_ALPHA = 0.92f

@Composable
private fun FolderDialogs(
    state: EditorUiState,
    folder: DeckSlot.Folder,
    at: SlotPosition,
    dialog: FolderDialog?,
    onDialog: (FolderDialog?) -> Unit,
    viewModel: EditorViewModel,
) {
    val close = { onDialog(null) }
    val full = Modifier.fillMaxWidth()
    when (dialog) {
        null -> {
            // nothing open
        }

        is FolderDialog.Item -> {
            val title = state.buttons[folder.actions.getOrNull(dialog.index)]?.title.orEmpty()
            DeckModal(title, onDismiss = close, dismissText = stringResource(R.string.editor_close)) {
                DeckListItem(
                    stringResource(R.string.editor_folder_item_change),
                    onClick = { onDialog(FolderDialog.Picker(dialog.index)) },
                    modifier = full,
                )
                DeckListItem(
                    stringResource(R.string.editor_folder_item_out),
                    onClick = {
                        viewModel.moveOutOfFolder(at, dialog.index)
                        close()
                    },
                    modifier = full,
                )
                DeckListItem(
                    stringResource(R.string.editor_folder_item_remove),
                    onClick = {
                        viewModel.removeFromFolder(at, dialog.index)
                        close()
                    },
                    modifier = full,
                )
            }
        }

        is FolderDialog.Picker -> {
            DeckModal(
                stringResource(R.string.editor_picker_title),
                onDismiss = close,
                dismissText = stringResource(R.string.editor_cancel),
            ) {
                if (state.actions.isEmpty()) DeckStatusMessage(stringResource(R.string.editor_picker_empty))
                state.actions.forEach { action ->
                    DeckListItem(
                        title = action.title,
                        onClick = {
                            if (dialog.index == null) {
                                viewModel.addToFolder(at, action.id)
                            } else {
                                viewModel.replaceInFolder(at, dialog.index, action.id)
                            }
                            close()
                        },
                        modifier = full,
                    )
                }
            }
        }

        FolderDialog.Rename -> {
            var name by remember { mutableStateOf(folder.name) }
            DeckModal(
                title = stringResource(R.string.editor_folder_name),
                onDismiss = close,
                confirmText = stringResource(R.string.editor_ok),
                onConfirm = {
                    viewModel.renameFolder(at, name)
                    close()
                },
                dismissText = stringResource(R.string.editor_cancel),
            ) {
                DeckTextField(
                    name,
                    { name = it.take(LayoutLimits.MAX_NAME_LENGTH) },
                    stringResource(R.string.editor_folder_name),
                    full,
                )
            }
        }
    }
}
