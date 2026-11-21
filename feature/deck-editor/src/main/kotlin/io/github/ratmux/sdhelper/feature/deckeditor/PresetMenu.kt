package io.github.ratmux.sdhelper.feature.deckeditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.ui.designapi.DeckListItem
import io.github.ratmux.sdhelper.ui.designapi.DeckModal
import io.github.ratmux.sdhelper.ui.designapi.DeckPopupHeading
import io.github.ratmux.sdhelper.ui.designapi.DeckPopupItem
import io.github.ratmux.sdhelper.ui.designapi.DeckPopupMenu
import io.github.ratmux.sdhelper.ui.designapi.DeckTextField
import org.koin.compose.viewmodel.koinViewModel

/** Which level of the "ready-made settings" menu is open. */
internal enum class PresetMenuState { CLOSED, MAIN, LIST }

private sealed interface PresetDialog {
    /** Name for a copy of the layout being edited ("Save"). */
    data object SaveName : PresetDialog

    /** Choose how a new layout starts: empty or from a ready-made one. */
    data object NewChoice : PresetDialog

    /** Name for the new layout; [presetId] is null for an empty one. */
    data class NewName(
        val presetId: String?,
        val suggestion: String,
    ) : PresetDialog

    /** New name for the layout being edited. */
    data object Rename : PresetDialog
}

/**
 * The "ready-made settings" menu of the editor and what follows from it: switch layout, save a copy, make a new
 * one (empty or from a ready-made one), name the current one. Page and grid-size entries are handed to the editor.
 */
@Composable
internal fun PresetMenu(
    menu: PresetMenuState,
    onMenu: (PresetMenuState) -> Unit,
    active: DeckProfile,
    onPages: () -> Unit,
    onGridSize: () -> Unit,
    viewModel: LayoutsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }
    val language = LocalConfiguration.current.locales[0].language
    val close = { onMenu(PresetMenuState.CLOSED) }

    when (menu) {
        PresetMenuState.CLOSED -> {
            // nothing open
        }

        PresetMenuState.MAIN -> {
            DeckPopupMenu(onDismiss = close) {
                DeckPopupItem(
                    label = stringResource(R.string.preset_menu),
                    iconKey = "list",
                    chevron = true,
                    onClick = { onMenu(PresetMenuState.LIST) },
                )
                DeckPopupItem(stringResource(R.string.preset_save), iconKey = "check", onClick = {
                    close()
                    dialog = PresetDialog.SaveName
                })
                DeckPopupItem(stringResource(R.string.preset_new), iconKey = "add", onClick = {
                    close()
                    dialog = PresetDialog.NewChoice
                })
                DeckPopupItem(stringResource(R.string.preset_rename), iconKey = "edit", onClick = {
                    close()
                    dialog = PresetDialog.Rename
                })
                DeckPopupItem(stringResource(R.string.preset_pages), iconKey = "pages", onClick = {
                    close()
                    onPages()
                })
                DeckPopupItem(stringResource(R.string.preset_grid), iconKey = "grid", onClick = {
                    close()
                    onGridSize()
                })
            }
        }

        PresetMenuState.LIST -> {
            DeckPopupMenu(onDismiss = close) {
                DeckPopupHeading(stringResource(R.string.preset_heading))
                state.profiles.forEach { profile ->
                    val selected = profile.id == state.activeProfileId
                    DeckPopupItem(
                        label = profile.name,
                        iconKey = if (selected) "check" else "apps",
                        selected = selected,
                        onClick = {
                            viewModel.activate(profile.id)
                            close()
                        },
                    )
                }
            }
        }
    }

    val full = Modifier.fillMaxWidth()
    val cancelText = stringResource(R.string.editor_cancel)
    val okText = stringResource(R.string.editor_ok)
    when (val open = dialog) {
        null -> {
            // no dialog open
        }

        PresetDialog.SaveName -> {
            NameDialog(active.name, okText, cancelText, onDismiss = { dialog = null }) { name ->
                viewModel.saveCopy(active.id, name)
                dialog = null
            }
        }

        PresetDialog.Rename -> {
            NameDialog(active.name, okText, cancelText, onDismiss = { dialog = null }) { name ->
                viewModel.rename(active.id, name)
                dialog = null
            }
        }

        PresetDialog.NewChoice -> {
            DeckModal(stringResource(R.string.preset_new), onDismiss = { dialog = null }, dismissText = cancelText) {
                DeckListItem(
                    title = stringResource(R.string.layouts_empty),
                    supporting = stringResource(R.string.layouts_empty_hint),
                    onClick = { dialog = PresetDialog.NewName(null, "") },
                    modifier = full,
                )
                LayoutPresets.all.forEach { preset ->
                    DeckListItem(
                        title = preset.title(language),
                        supporting = preset.description(language),
                        onClick = { dialog = PresetDialog.NewName(preset.id, preset.title(language)) },
                        modifier = full,
                    )
                }
            }
        }

        is PresetDialog.NewName -> {
            NameDialog(open.suggestion, okText, cancelText, onDismiss = { dialog = PresetDialog.NewChoice }) { name ->
                if (open.presetId == null) viewModel.createEmpty(name) else viewModel.addFromPreset(open.presetId, name)
                dialog = null
            }
        }
    }
}

@Composable
private fun NameDialog(
    initial: String,
    okText: String,
    cancelText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    DeckModal(
        title = stringResource(R.string.layouts_name_title),
        onDismiss = onDismiss,
        confirmText = okText,
        onConfirm = { onConfirm(name) },
        dismissText = cancelText,
    ) {
        DeckTextField(
            name,
            { name = it.take(LayoutLimits.MAX_NAME_LENGTH) },
            stringResource(R.string.layouts_name_title),
            Modifier.fillMaxWidth(),
        )
    }
}
