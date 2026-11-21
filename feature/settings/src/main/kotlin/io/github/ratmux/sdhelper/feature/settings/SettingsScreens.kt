package io.github.ratmux.sdhelper.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.ui.designapi.DeckChoiceRow
import io.github.ratmux.sdhelper.ui.designapi.DeckDialog
import io.github.ratmux.sdhelper.ui.designapi.DeckListItem
import io.github.ratmux.sdhelper.ui.designapi.DeckModal
import io.github.ratmux.sdhelper.ui.designapi.DeckRowEnd
import io.github.ratmux.sdhelper.ui.designapi.DeckScaffold
import io.github.ratmux.sdhelper.ui.designapi.DeckSegment
import io.github.ratmux.sdhelper.ui.designapi.DeckSegmentedChoice
import io.github.ratmux.sdhelper.ui.designapi.DeckSettingsGroup
import io.github.ratmux.sdhelper.ui.designapi.DeckSettingsRow
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckText
import io.github.ratmux.sdhelper.ui.designapi.DeckTextField
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import org.koin.compose.viewmodel.koinViewModel

private val ContentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)

/** The settings list: Stream Deck, Touch Pad, Keyboard and, last, General. */
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenStreamDeck: () -> Unit,
    onOpenGeneral: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comingSoon = stringResource(R.string.settings_coming_soon)
    DeckScaffold(title = stringResource(R.string.settings_title), modifier = modifier, onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(ContentPadding)) {
            DeckSettingsGroup {
                DeckSettingsRow(
                    title = stringResource(R.string.settings_stream_deck),
                    summary = stringResource(R.string.settings_stream_deck_summary),
                    iconKey = "apps",
                    end = DeckRowEnd.Chevron,
                    onClick = onOpenStreamDeck,
                )
                // announced, not built yet: no click handler, so touching them does nothing
                DeckSettingsRow(
                    title = stringResource(R.string.settings_touch_pad),
                    summary = stringResource(R.string.settings_touch_pad_summary),
                    iconKey = "touchpad",
                    end = DeckRowEnd.Badge(comingSoon),
                )
                DeckSettingsRow(
                    title = stringResource(R.string.settings_keyboard),
                    summary = stringResource(R.string.settings_keyboard_summary),
                    iconKey = "keyboard",
                    end = DeckRowEnd.Badge(comingSoon),
                )
                DeckSettingsRow(
                    title = stringResource(R.string.settings_general),
                    summary = stringResource(R.string.settings_general_summary),
                    iconKey = "general",
                    end = DeckRowEnd.Chevron,
                    onClick = onOpenGeneral,
                )
            }
        }
    }
}

/** General: the design language and light or dark. The theme changes on this same page. */
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = state.settings.themeMode.isDark(isSystemInDarkTheme())
    DeckScaffold(title = stringResource(R.string.settings_general), modifier = modifier, onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(ContentPadding)) {
            DeckSettingsGroup {
                DeckSettingsRow(
                    title = stringResource(R.string.general_design),
                    summary = stringResource(R.string.general_design_summary),
                    iconKey = "palette",
                )
                DeckChoiceRow(
                    title = stringResource(R.string.design_material),
                    summary = stringResource(R.string.design_material_summary),
                    iconKey = "palette",
                    selected = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // announced, not built yet: it does not react to touch
                DeckChoiceRow(
                    title = stringResource(R.string.design_miuix),
                    summary = stringResource(R.string.design_miuix_summary),
                    iconKey = "palette",
                    selected = false,
                    badge = stringResource(R.string.settings_coming_soon),
                )
                DeckSettingsRow(
                    title = stringResource(R.string.general_theme),
                    summary = stringResource(R.string.general_theme_summary),
                    iconKey = "theme",
                )
                DeckSegmentedChoice(
                    options =
                        listOf(
                            DeckSegment(THEME_LIGHT, stringResource(R.string.theme_light), "light"),
                            DeckSegment(THEME_DARK, stringResource(R.string.theme_dark), "dark"),
                        ),
                    selectedId = if (dark) THEME_DARK else THEME_LIGHT,
                    onSelect = { viewModel.setDarkTheme(it == THEME_DARK) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private const val THEME_LIGHT = "light"
private const val THEME_DARK = "dark"

private sealed interface LayoutDialog {
    data class Menu(
        val id: String,
    ) : LayoutDialog

    data class Rename(
        val id: String,
    ) : LayoutDialog

    data class Delete(
        val id: String,
    ) : LayoutDialog
}

/** Stream Deck settings: vibration, keeping the screen on, and the saved layouts (the "ready-made settings"). */
@Composable
fun StreamDeckSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LayoutDialog?>(null) }
    DeckScaffold(
        title = stringResource(R.string.settings_stream_deck),
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(ContentPadding)) {
            DeckSettingsGroup {
                DeckSettingsRow(
                    title = stringResource(R.string.deck_haptic),
                    summary = stringResource(R.string.deck_haptic_summary),
                    end = DeckRowEnd.Toggle(state.settings.hapticFeedback, viewModel::setHapticFeedback),
                )
                DeckSettingsRow(
                    title = stringResource(R.string.deck_keep_screen_on),
                    summary = stringResource(R.string.deck_keep_screen_on_summary),
                    end = DeckRowEnd.Toggle(state.settings.keepScreenOn, viewModel::setKeepScreenOn),
                )
            }
            DeckText(
                stringResource(R.string.deck_layouts),
                style = DeckTextStyle.SECTION,
                modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 8.dp),
            )
            if (state.message == SettingsMessage.LAST_LAYOUT_CANNOT_BE_DELETED) {
                DeckStatusMessage(
                    stringResource(R.string.deck_last_layout),
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    DeckTone.ERROR,
                )
            }
            DeckSettingsGroup {
                state.profiles.forEach { profile ->
                    DeckSettingsRow(
                        title = profile.name,
                        summary =
                            stringResource(
                                R.string.deck_layout_summary,
                                profile.pages.size,
                                profile.grid.columns,
                                profile.grid.rows,
                            ),
                        end = if (profile.id == state.activeProfileId) DeckRowEnd.Check else DeckRowEnd.Chevron,
                        onClick = { dialog = LayoutDialog.Menu(profile.id) },
                    )
                }
            }
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

        is LayoutDialog.Menu -> {
            profileOf(open.id)?.let { profile ->
                DeckModal(profile.name, onDismiss = close, dismissText = stringResource(R.string.settings_close)) {
                    val full = Modifier.fillMaxWidth()
                    DeckListItem(
                        stringResource(R.string.deck_use),
                        onClick = {
                            viewModel.activate(profile.id)
                            close()
                        },
                        modifier = full,
                    )
                    DeckListItem(stringResource(R.string.deck_rename), onClick = {
                        dialog =
                            LayoutDialog.Rename(profile.id)
                    }, modifier = full)
                    DeckListItem(
                        stringResource(R.string.deck_copy),
                        onClick = {
                            viewModel.duplicate(profile.id)
                            close()
                        },
                        modifier = full,
                    )
                    DeckListItem(stringResource(R.string.deck_delete), onClick = {
                        dialog =
                            LayoutDialog.Delete(profile.id)
                    }, modifier = full)
                }
            }
        }

        is LayoutDialog.Rename -> {
            profileOf(open.id)?.let { profile ->
                var name by remember { mutableStateOf(profile.name) }
                DeckModal(
                    title = stringResource(R.string.deck_name),
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
                    }, stringResource(R.string.deck_name), Modifier.fillMaxWidth())
                }
            }
        }

        is LayoutDialog.Delete -> {
            profileOf(open.id)?.let { profile ->
                DeckDialog(
                    title = stringResource(R.string.deck_delete_title),
                    text = stringResource(R.string.deck_delete_message, profile.name),
                    confirmText = stringResource(R.string.deck_delete_confirm),
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
