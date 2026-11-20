package io.github.ratmux.sdhelper.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.ui.designapi.DeckButton
import io.github.ratmux.sdhelper.ui.designapi.DeckDialog
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckListItem
import io.github.ratmux.sdhelper.ui.designapi.DeckModal
import io.github.ratmux.sdhelper.ui.designapi.DeckScaffold
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckSwitchRow
import io.github.ratmux.sdhelper.ui.designapi.DeckText
import io.github.ratmux.sdhelper.ui.designapi.DeckTextField
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import org.koin.compose.viewmodel.koinViewModel

private sealed interface Dialog {
    data object Presets : Dialog

    data class Layout(
        val id: String,
    ) : Dialog

    data class Rename(
        val id: String,
    ) : Dialog

    data class Delete(
        val id: String,
    ) : Dialog
}

/** App settings and layout management (choose, add a ready-made one, copy, rename, delete). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val language = LocalConfiguration.current.locales[0].language

    DeckScaffold(title = stringResource(R.string.settings_title), modifier = modifier, onBack = onBack) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeckSwitchRow(
                stringResource(R.string.settings_haptic),
                state.settings.hapticFeedback,
                viewModel::setHapticFeedback,
                Modifier.fillMaxWidth(),
            )
            DeckSwitchRow(
                stringResource(R.string.settings_keep_screen_on),
                state.settings.keepScreenOn,
                viewModel::setKeepScreenOn,
                Modifier.fillMaxWidth(),
            )
            DeckText(
                stringResource(R.string.settings_layouts),
                style = DeckTextStyle.TITLE,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (state.message == SettingsMessage.LAST_LAYOUT_CANNOT_BE_DELETED) {
                DeckStatusMessage(stringResource(R.string.settings_last_layout), tone = DeckTone.ERROR)
            }
            state.profiles.forEach { profile ->
                DeckListItem(
                    title = profile.name,
                    supporting =
                        stringResource(
                            R.string.settings_layout_summary,
                            profile.pages.size,
                            profile.grid.columns,
                            profile.grid.rows,
                        ),
                    trailing =
                        if (profile.id ==
                            state.activeProfileId
                        ) {
                            stringResource(R.string.settings_layout_active)
                        } else {
                            null
                        },
                    onClick = { dialog = Dialog.Layout(profile.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DeckButton(
                text = stringResource(R.string.settings_add_preset),
                onClick = { dialog = Dialog.Presets },
                emphasis = DeckEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val close = {
        dialog = null
        viewModel.dismissMessage()
    }
    val profileOf = { id: String -> state.profiles.firstOrNull { it.id == id } }

    when (val open = dialog) {
        null -> {
            // no dialog open
        }

        Dialog.Presets -> {
            DeckModal(
                stringResource(R.string.settings_preset_title),
                onDismiss = close,
                dismissText = stringResource(R.string.settings_close),
            ) {
                LayoutPresets.all.forEach { preset ->
                    DeckListItem(
                        title = preset.title(language),
                        supporting = preset.description(language),
                        onClick = {
                            viewModel.addFromPreset(preset.id)
                            close()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        is Dialog.Layout -> {
            profileOf(open.id)?.let { profile ->
                LayoutMenu(profile, close, onUse = {
                    viewModel.activate(profile.id)
                    close()
                }, onRename = { dialog = Dialog.Rename(profile.id) }, onCopy = {
                    viewModel.duplicate(profile.id)
                    close()
                }, onDelete = { dialog = Dialog.Delete(profile.id) })
            }
        }

        is Dialog.Rename -> {
            profileOf(open.id)?.let { profile ->
                var name by remember { mutableStateOf(profile.name) }
                DeckModal(
                    title = stringResource(R.string.settings_rename_title),
                    onDismiss = close,
                    confirmText = stringResource(R.string.settings_ok),
                    onConfirm = {
                        viewModel.rename(profile.id, name)
                        close()
                    },
                    dismissText = stringResource(R.string.settings_cancel),
                ) {
                    DeckTextField(name, {
                        name = it.take(LayoutLimits.MAX_NAME_LENGTH)
                    }, stringResource(R.string.settings_rename_title), Modifier.fillMaxWidth())
                }
            }
        }

        is Dialog.Delete -> {
            profileOf(open.id)?.let { profile ->
                DeckDialog(
                    title = stringResource(R.string.settings_delete_title),
                    text = stringResource(R.string.settings_delete_message, profile.name),
                    confirmText = stringResource(R.string.settings_delete_confirm),
                    onConfirm = {
                        viewModel.delete(profile.id)
                        dialog = null
                    },
                    onDismiss = close,
                    dismissText = stringResource(R.string.settings_cancel),
                )
            }
        }
    }
}

@Composable
private fun LayoutMenu(
    profile: DeckProfile,
    onClose: () -> Unit,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    DeckModal(profile.name, onDismiss = onClose, dismissText = stringResource(R.string.settings_close)) {
        val modifier = Modifier.fillMaxWidth()
        DeckListItem(stringResource(R.string.settings_use), onClick = onUse, modifier = modifier)
        DeckListItem(stringResource(R.string.settings_rename), onClick = onRename, modifier = modifier)
        DeckListItem(stringResource(R.string.settings_duplicate), onClick = onCopy, modifier = modifier)
        DeckListItem(stringResource(R.string.settings_delete), onClick = onDelete, modifier = modifier)
    }
}
