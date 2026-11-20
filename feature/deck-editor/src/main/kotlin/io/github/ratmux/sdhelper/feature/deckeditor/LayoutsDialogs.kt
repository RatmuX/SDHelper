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
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.ui.designapi.DeckDialog
import io.github.ratmux.sdhelper.ui.designapi.DeckListItem
import io.github.ratmux.sdhelper.ui.designapi.DeckModal
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckTextField
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import org.koin.compose.viewmodel.koinViewModel

private sealed interface Step {
    data object List : Step

    data object NewChoice : Step

    /** Name the new layout; [presetId] is null for an empty one. */
    data class NewName(
        val presetId: String?,
        val suggestion: String,
    ) : Step

    data class Menu(
        val id: String,
    ) : Step

    data class Rename(
        val id: String,
    ) : Step

    data class Delete(
        val id: String,
    ) : Step
}

/**
 * Everything about whole layouts, reached from the editor: switch layout, start a new one (empty or ready-made,
 * under a name of the person's choice), copy, rename, delete.
 */
@Composable
fun LayoutsDialogs(
    onClose: () -> Unit,
    viewModel: LayoutsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf<Step>(Step.List) }
    val language = LocalConfiguration.current.locales[0].language
    val close = {
        viewModel.dismissMessage()
        onClose()
    }
    val full = Modifier.fillMaxWidth()
    val closeText = stringResource(R.string.editor_close)
    val cancelText = stringResource(R.string.editor_cancel)
    val okText = stringResource(R.string.editor_ok)

    when (val current = step) {
        Step.List -> {
            DeckModal(stringResource(R.string.layouts_title), onDismiss = close, dismissText = closeText) {
                if (state.message == LayoutsMessage.LAST_LAYOUT_CANNOT_BE_DELETED) {
                    DeckStatusMessage(stringResource(R.string.layouts_last), tone = DeckTone.ERROR)
                }
                state.profiles.forEach { profile ->
                    DeckListItem(
                        title = profile.name,
                        supporting =
                            stringResource(
                                R.string.layouts_summary,
                                profile.pages.size,
                                profile.grid.columns,
                                profile.grid.rows,
                            ),
                        trailing =
                            if (profile.id ==
                                state.activeProfileId
                            ) {
                                stringResource(R.string.layouts_active)
                            } else {
                                null
                            },
                        onClick = { step = Step.Menu(profile.id) },
                        modifier = full,
                    )
                }
                DeckListItem(stringResource(R.string.layouts_new), onClick = { step = Step.NewChoice }, modifier = full)
            }
        }

        Step.NewChoice -> {
            DeckModal(
                stringResource(R.string.layouts_new),
                onDismiss = { step = Step.List },
                dismissText = cancelText,
            ) {
                DeckListItem(
                    title = stringResource(R.string.layouts_empty),
                    supporting = stringResource(R.string.layouts_empty_hint),
                    onClick = { step = Step.NewName(null, "") },
                    modifier = full,
                )
                LayoutPresets.all.forEach { preset ->
                    DeckListItem(
                        title = preset.title(language),
                        supporting = preset.description(language),
                        onClick = { step = Step.NewName(preset.id, preset.title(language)) },
                        modifier = full,
                    )
                }
            }
        }

        is Step.NewName -> {
            var name by remember { mutableStateOf(current.suggestion) }
            DeckModal(
                title = stringResource(R.string.layouts_name_title),
                onDismiss = { step = Step.NewChoice },
                confirmText = okText,
                onConfirm = {
                    if (current.presetId ==
                        null
                    ) {
                        viewModel.createEmpty(name)
                    } else {
                        viewModel.addFromPreset(current.presetId, name)
                    }
                    close()
                },
                dismissText = cancelText,
            ) {
                DeckTextField(
                    name,
                    { name = it.take(LayoutLimits.MAX_NAME_LENGTH) },
                    stringResource(R.string.layouts_name_title),
                    full,
                )
            }
        }

        is Step.Menu -> {
            val profile = state.profiles.firstOrNull { it.id == current.id }
            if (profile == null) {
                step = Step.List
            } else {
                DeckModal(profile.name, onDismiss = { step = Step.List }, dismissText = closeText) {
                    DeckListItem(
                        stringResource(R.string.layouts_use),
                        onClick = {
                            viewModel.activate(profile.id)
                            close()
                        },
                        modifier = full,
                    )
                    DeckListItem(
                        stringResource(R.string.layouts_rename),
                        onClick = { step = Step.Rename(profile.id) },
                        modifier = full,
                    )
                    DeckListItem(
                        stringResource(R.string.layouts_copy),
                        onClick = {
                            viewModel.duplicate(profile.id)
                            step = Step.List
                        },
                        modifier = full,
                    )
                    DeckListItem(
                        stringResource(R.string.layouts_delete),
                        onClick = { step = Step.Delete(profile.id) },
                        modifier = full,
                    )
                }
            }
        }

        is Step.Rename -> {
            val profile = state.profiles.firstOrNull { it.id == current.id }
            var name by remember { mutableStateOf(profile?.name.orEmpty()) }
            DeckModal(
                title = stringResource(R.string.layouts_name_title),
                onDismiss = { step = Step.List },
                confirmText = okText,
                onConfirm = {
                    viewModel.rename(current.id, name)
                    step = Step.List
                },
                dismissText = cancelText,
            ) {
                DeckTextField(
                    name,
                    { name = it.take(LayoutLimits.MAX_NAME_LENGTH) },
                    stringResource(R.string.layouts_name_title),
                    full,
                )
            }
        }

        is Step.Delete -> {
            val name =
                state.profiles
                    .firstOrNull { it.id == current.id }
                    ?.name
                    .orEmpty()
            DeckDialog(
                title = stringResource(R.string.layouts_delete_title),
                text = stringResource(R.string.layouts_delete_message, name),
                confirmText = stringResource(R.string.layouts_delete_confirm),
                onConfirm = {
                    viewModel.delete(current.id)
                    step = Step.List
                },
                onDismiss = { step = Step.List },
                dismissText = cancelText,
            )
        }
    }
}
