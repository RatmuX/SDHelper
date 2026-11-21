package io.github.ratmux.sdhelper.feature.deckeditor

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.GridSize
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.SlotPosition
import io.github.ratmux.sdhelper.ui.components.DeckGridPage
import io.github.ratmux.sdhelper.ui.components.EdgeDwell
import io.github.ratmux.sdhelper.ui.components.GridGeometry
import io.github.ratmux.sdhelper.ui.components.GridGestureHandler
import io.github.ratmux.sdhelper.ui.components.ImmersiveLandscape
import io.github.ratmux.sdhelper.ui.components.gridGestures
import io.github.ratmux.sdhelper.ui.designapi.DeckBackground
import io.github.ratmux.sdhelper.ui.designapi.DeckButton
import io.github.ratmux.sdhelper.ui.designapi.DeckButtonTile
import io.github.ratmux.sdhelper.ui.designapi.DeckDialog
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
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private const val MESSAGE_MILLIS = 4_000L
private const val DWELL_POLL_MILLIS = 50L

/** A button being dragged: where it came from and where the finger is, in the pager's pixel space. */
private data class DragState(
    val from: SlotPosition,
    val slot: DeckSlot,
    val pointer: Offset,
)

/** Which dialog is open. One at a time keeps the editor simple. */
private sealed interface Dialog {
    data class Cell(
        val at: SlotPosition,
    ) : Dialog

    data class Picker(
        val at: SlotPosition,
    ) : Dialog

    data class MoveToPage(
        val from: SlotPosition,
    ) : Dialog

    data object PageOptions : Dialog

    data class NewFolder(
        val at: SlotPosition,
    ) : Dialog

    data class RenameFolder(
        val at: SlotPosition,
    ) : Dialog

    data class DeleteFolder(
        val at: SlotPosition,
    ) : Dialog

    data object Layouts : Dialog

    data object RenamePage : Dialog

    data object DeletePage : Dialog

    data object GridSizes : Dialog
}

/**
 * The editor of the active layout: landscape like the control panel, so what is edited is what is used. Tap a cell
 * for its menu, or press and hold a button and drag it to another cell (also on another page: hold it at the edge;
 * dropped on a folder, a button goes into the folder).
 */
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImmersiveLandscape(keepScreenOn = false)
    DeckBackground(modifier) {
        val profile = state.profile ?: return@DeckBackground
        key(profile.id) { EditorBody(state, profile, viewModel) }
    }
}

@Composable
private fun EditorBody(
    state: EditorUiState,
    profile: DeckProfile,
    viewModel: EditorViewModel,
) {
    val pager = rememberPagerState { profile.pages.size }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var openFolder by remember { mutableStateOf<SlotPosition?>(null) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var geometry by remember { mutableStateOf<GridGeometry?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val dwell = remember { EdgeDwell() }
    val latestState by rememberUpdatedState(state)
    val latestProfile by rememberUpdatedState(profile)

    // Holding a dragged button near an edge turns the page.
    LaunchedEffect(drag != null) {
        dwell.reset()
        while (drag != null) {
            val pointer = drag?.pointer
            val direction =
                pointer?.let {
                    dwell.update(
                        it.x,
                        containerSize.width.toFloat(),
                        SystemClock.elapsedRealtime(),
                    )
                }
            when (direction) {
                EdgeDwell.Direction.NEXT -> {
                    if (pager.currentPage <
                        pager.pageCount - 1
                    ) {
                        pager.animateScrollToPage(pager.currentPage + 1)
                    }
                }

                EdgeDwell.Direction.PREVIOUS -> {
                    if (pager.currentPage >
                        0
                    ) {
                        pager.animateScrollToPage(pager.currentPage - 1)
                    }
                }

                null -> {
                    // nothing to do
                }
            }
            delay(DWELL_POLL_MILLIS)
        }
    }

    val handler =
        remember(pager) {
            object : GridGestureHandler {
                override fun onTap(index: Int) {
                    dialog = Dialog.Cell(SlotPosition(pager.currentPage, index))
                }

                override fun onDragStart(
                    index: Int,
                    position: Offset,
                ) {
                    val from = SlotPosition(pager.currentPage, index)
                    val slot = latestProfile.slotAt(from) ?: return
                    drag = DragState(from, slot, position)
                }

                override fun onDrag(position: Offset) {
                    drag = drag?.copy(pointer = position)
                }

                override fun onDragEnd() {
                    val finished = drag
                    drag = null
                    val target = finished?.let { geometry?.cellAt(it.pointer.x, it.pointer.y) }
                    if (finished != null && target != null) {
                        viewModel.move(finished.from, SlotPosition(pager.currentPage, target))
                    }
                }

                override fun onDragCancel() {
                    drag = null
                }
            }
        }

    val openedFolder = openFolder?.let { latestProfile.slotAt(it) as? DeckSlot.Folder }
    BackHandler(enabled = openedFolder != null) { openFolder = null }
    if (openFolder != null && openedFolder == null) openFolder = null

    if (openedFolder != null) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            FolderEditor(
                state,
                profile,
                checkNotNull(openFolder),
                openedFolder,
                viewModel,
                onBack = { openFolder = null },
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
        Header(
            state,
            profile,
            pager,
            onPageOptions = { dialog = Dialog.PageOptions },
            onLayouts = { dialog = Dialog.Layouts },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = it }
                .gridGestures(enabled = true, geometry = { geometry }, handler = handler),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = drag == null,
            ) { pageIndex ->
                DeckGridPage(
                    columns = profile.grid.columns,
                    rows = profile.grid.rows,
                    onGeometry = { geometry = it },
                ) { index, cell ->
                    val position = SlotPosition(pageIndex, index)
                    val hovered =
                        drag?.let { d ->
                            pageIndex == pager.currentPage &&
                                geometry?.cellAt(d.pointer.x, d.pointer.y) == index
                        } ==
                            true
                    when (val slot = profile.pages[pageIndex].slots[index]) {
                        null -> {
                            DeckEmptyTile(modifier = cell, highlighted = hovered)
                        }

                        else -> {
                            SlotTile(
                                slot,
                                state,
                                cell,
                                enabled = drag?.from != position,
                                highlighted = hovered,
                            )
                        }
                    }
                }
            }
            drag?.let { d ->
                val g = geometry
                if (g != null) {
                    val tile = with(LocalDensity.current) { g.tile.toDp() }
                    SlotTile(
                        d.slot,
                        state,
                        Modifier
                            .zIndex(1f)
                            .offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (d.pointer.x - g.tile / 2).roundToInt(),
                                    (d.pointer.y - g.tile / 2).roundToInt(),
                                )
                            }.size(tile)
                            .alpha(0.92f),
                        enabled = true,
                        highlighted = true,
                    )
                }
            }
        }
    }

    Dialogs(state, profile, pager, dialog, onDialog = { dialog = it }, onOpenFolder = { openFolder = it }, viewModel)
}

@Composable
private fun Header(
    state: EditorUiState,
    profile: DeckProfile,
    pager: PagerState,
    onPageOptions: () -> Unit,
    onLayouts: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckButton(
            text = profile.name,
            onClick = onLayouts,
            emphasis = DeckEmphasis.SECONDARY,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val notice = state.message
            if (notice != null) {
                DeckStatusMessage(
                    text =
                        when (notice) {
                            EditorMessage.GRID_DOES_NOT_FIT -> {
                                stringResource(R.string.editor_message_grid_does_not_fit)
                            }

                            EditorMessage.PAGE_FULL -> {
                                stringResource(R.string.editor_message_page_full)
                            }

                            EditorMessage.FOLDER_FULL -> {
                                stringResource(R.string.editor_message_folder_full)
                            }

                            EditorMessage.NO_FREE_CELL -> {
                                stringResource(R.string.editor_message_no_free_cell)
                            }

                            EditorMessage.PAGE_LIMIT -> {
                                stringResource(
                                    R.string.editor_message_page_limit,
                                    LayoutLimits.MAX_PAGES,
                                )
                            }
                        },
                    tone = DeckTone.ERROR,
                )
            } else {
                DeckText(
                    profile.pages
                        .getOrNull(pager.currentPage)
                        ?.name
                        .orEmpty(),
                    style = DeckTextStyle.TITLE,
                )
                DeckText(
                    stringResource(R.string.editor_hint),
                    style = DeckTextStyle.CAPTION,
                )
            }
        }
        DeckButton(
            text = stringResource(R.string.editor_page_options),
            onClick = onPageOptions,
            emphasis = DeckEmphasis.SECONDARY,
        )
        if (profile.pages.size > 1) DeckPageIndicator(count = profile.pages.size, current = pager.currentPage)
    }
}

@Composable
private fun Dialogs(
    state: EditorUiState,
    profile: DeckProfile,
    pager: PagerState,
    dialog: Dialog?,
    onDialog: (Dialog?) -> Unit,
    onOpenFolder: (SlotPosition) -> Unit,
    viewModel: EditorViewModel,
) {
    val close = { onDialog(null) }
    val page = pager.currentPage

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MILLIS)
            viewModel.dismissMessage()
        }
    }

    when (dialog) {
        null -> {
            // nothing to do
        }

        is Dialog.Cell -> {
            val slot = profile.slotAt(dialog.at)
            val full = Modifier.fillMaxWidth()
            DeckModal(
                stringResource(R.string.editor_cell_title),
                onDismiss = close,
                dismissText = stringResource(R.string.editor_close),
            ) {
                when (slot) {
                    is DeckSlot.Folder -> {
                        DeckListItem(
                            title = stringResource(R.string.editor_cell_open_folder),
                            onClick = {
                                onOpenFolder(dialog.at)
                                close()
                            },
                            modifier = full,
                        )
                        DeckListItem(
                            title = stringResource(R.string.editor_cell_rename_folder),
                            onClick = { onDialog(Dialog.RenameFolder(dialog.at)) },
                            modifier = full,
                        )
                    }

                    else -> {
                        DeckListItem(
                            title = stringResource(R.string.editor_cell_choose),
                            onClick = { onDialog(Dialog.Picker(dialog.at)) },
                            modifier = full,
                        )
                        if (slot == null) {
                            DeckListItem(
                                title = stringResource(R.string.editor_cell_make_folder),
                                onClick = { onDialog(Dialog.NewFolder(dialog.at)) },
                                modifier = full,
                            )
                        }
                    }
                }
                if (slot != null) {
                    DeckListItem(
                        title =
                            stringResource(
                                if (slot is DeckSlot.Folder) {
                                    R.string.editor_cell_remove_folder
                                } else {
                                    R.string.editor_cell_clear
                                },
                            ),
                        onClick = {
                            if (slot is DeckSlot.Folder) {
                                onDialog(Dialog.DeleteFolder(dialog.at))
                            } else {
                                viewModel.clear(dialog.at)
                                close()
                            }
                        },
                        modifier = full,
                    )
                    if (profile.pages.size > 1) {
                        DeckListItem(
                            title = stringResource(R.string.editor_cell_move_to_page),
                            onClick = { onDialog(Dialog.MoveToPage(dialog.at)) },
                            modifier = full,
                        )
                    }
                }
            }
        }

        is Dialog.NewFolder -> {
            val defaultName = stringResource(R.string.editor_default_folder)
            var name by remember { mutableStateOf(defaultName) }
            DeckModal(
                title = stringResource(R.string.editor_folder_name),
                onDismiss = close,
                confirmText = stringResource(R.string.editor_ok),
                onConfirm = {
                    viewModel.createFolder(dialog.at, name)
                    onOpenFolder(dialog.at)
                    close()
                },
                dismissText = stringResource(R.string.editor_cancel),
            ) {
                DeckTextField(
                    name,
                    { name = it.take(LayoutLimits.MAX_NAME_LENGTH) },
                    stringResource(R.string.editor_folder_name),
                    Modifier.fillMaxWidth(),
                )
            }
        }

        is Dialog.RenameFolder -> {
            var name by remember { mutableStateOf((profile.slotAt(dialog.at) as? DeckSlot.Folder)?.name.orEmpty()) }
            DeckModal(
                title = stringResource(R.string.editor_folder_name),
                onDismiss = close,
                confirmText = stringResource(R.string.editor_ok),
                onConfirm = {
                    viewModel.renameFolder(dialog.at, name)
                    close()
                },
                dismissText = stringResource(R.string.editor_cancel),
            ) {
                DeckTextField(
                    name,
                    { name = it.take(LayoutLimits.MAX_NAME_LENGTH) },
                    stringResource(R.string.editor_folder_name),
                    Modifier.fillMaxWidth(),
                )
            }
        }

        is Dialog.DeleteFolder -> {
            val folder = profile.slotAt(dialog.at) as? DeckSlot.Folder
            DeckDialog(
                title = stringResource(R.string.editor_folder_delete_title),
                text =
                    stringResource(
                        R.string.editor_folder_delete_message,
                        folder?.name.orEmpty(),
                        folder?.actions?.size ?: 0,
                    ),
                confirmText = stringResource(R.string.editor_delete_confirm),
                onConfirm = {
                    viewModel.clear(dialog.at)
                    close()
                },
                onDismiss = close,
                dismissText = stringResource(R.string.editor_cancel),
            )
        }

        is Dialog.Picker -> {
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
                            viewModel.assign(dialog.at, action.id)
                            close()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        is Dialog.MoveToPage -> {
            DeckModal(
                stringResource(R.string.editor_move_title),
                onDismiss = close,
                dismissText = stringResource(R.string.editor_cancel),
            ) {
                profile.pages.forEachIndexed { index, target ->
                    if (index != dialog.from.page) {
                        DeckListItem(
                            title = target.name,
                            onClick = {
                                viewModel.moveToPage(dialog.from, index)
                                close()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Dialog.Layouts -> {
            LayoutsDialogs(onClose = close)
        }

        Dialog.PageOptions -> {
            DeckModal(
                stringResource(R.string.editor_page_title),
                onDismiss = close,
                dismissText = stringResource(R.string.editor_close),
            ) {
                val modifier = Modifier.fillMaxWidth()
                DeckListItem(stringResource(R.string.editor_page_rename), onClick = {
                    onDialog(Dialog.RenamePage)
                }, modifier = modifier)
                DeckListItem(
                    stringResource(R.string.editor_page_add),
                    onClick = {
                        viewModel.addPage()
                        close()
                    },
                    modifier = modifier,
                )
                if (page > 0) {
                    DeckListItem(
                        stringResource(R.string.editor_page_left),
                        onClick = {
                            viewModel.reorderPage(page, page - 1)
                            close()
                        },
                        modifier = modifier,
                    )
                }
                if (page < profile.pages.size - 1) {
                    DeckListItem(
                        stringResource(R.string.editor_page_right),
                        onClick = {
                            viewModel.reorderPage(page, page + 1)
                            close()
                        },
                        modifier = modifier,
                    )
                }
                DeckListItem(
                    stringResource(R.string.editor_page_grid),
                    onClick = { onDialog(Dialog.GridSizes) },
                    modifier = modifier,
                )
                if (profile.pages.size > 1) {
                    DeckListItem(stringResource(R.string.editor_page_delete), onClick = {
                        onDialog(Dialog.DeletePage)
                    }, modifier = modifier)
                }
            }
        }

        Dialog.RenamePage -> {
            var name by remember {
                mutableStateOf(
                    profile.pages
                        .getOrNull(page)
                        ?.name
                        .orEmpty(),
                )
            }
            DeckModal(
                title = stringResource(R.string.editor_rename_title),
                onDismiss = close,
                confirmText = stringResource(R.string.editor_ok),
                onConfirm = {
                    viewModel.renamePage(page, name)
                    close()
                },
                dismissText = stringResource(R.string.editor_cancel),
            ) {
                DeckTextField(name, {
                    name = it.take(LayoutLimits.MAX_NAME_LENGTH)
                }, stringResource(R.string.editor_rename_title), Modifier.fillMaxWidth())
            }
        }

        Dialog.DeletePage -> {
            DeckDialog(
                title = stringResource(R.string.editor_delete_title),
                text =
                    stringResource(
                        R.string.editor_delete_message,
                        profile.pages
                            .getOrNull(page)
                            ?.name
                            .orEmpty(),
                    ),
                confirmText = stringResource(R.string.editor_delete_confirm),
                onConfirm = {
                    viewModel.removePage(page)
                    close()
                },
                onDismiss = close,
                dismissText = stringResource(R.string.editor_cancel),
            )
        }

        Dialog.GridSizes -> {
            DeckModal(
                stringResource(R.string.editor_grid_title),
                onDismiss = close,
                dismissText = stringResource(R.string.editor_cancel),
            ) {
                GridSize.ALLOWED.forEach { size ->
                    DeckListItem(
                        title = "${size.columns} × ${size.rows}",
                        trailing = if (size == profile.grid) stringResource(R.string.editor_grid_current) else null,
                        onClick = {
                            viewModel.requestGrid(size)
                            close()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    state.pendingResize?.let { resize ->
        DeckDialog(
            title = stringResource(R.string.editor_grid_confirm_title),
            text = stringResource(R.string.editor_grid_confirm_message, resize.movedButtons),
            confirmText = stringResource(R.string.editor_grid_confirm),
            onConfirm = viewModel::confirmResize,
            onDismiss = viewModel::cancelResize,
            dismissText = stringResource(R.string.editor_cancel),
        )
    }
}

@Composable
private fun SlotTile(
    slot: DeckSlot,
    state: EditorUiState,
    modifier: Modifier,
    enabled: Boolean,
    highlighted: Boolean,
) {
    when (slot) {
        is DeckSlot.Action -> {
            val button = state.buttons[slot.actionId]
            DeckButtonTile(
                title = button?.title ?: slot.actionId,
                modifier = modifier,
                iconKey = button?.iconKey,
                enabled = enabled,
                highlighted = highlighted,
            )
        }

        is DeckSlot.Folder -> {
            DeckButtonTile(
                title = slot.name,
                modifier = modifier,
                iconKey = FOLDER_ICON,
                enabled = enabled,
                highlighted = highlighted,
            )
        }
    }
}

private const val FOLDER_ICON = "folder"
