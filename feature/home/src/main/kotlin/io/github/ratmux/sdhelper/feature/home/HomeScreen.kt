package io.github.ratmux.sdhelper.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.AppMode
import io.github.ratmux.sdhelper.ui.designapi.DeckDrawer
import io.github.ratmux.sdhelper.ui.designapi.DeckDrawerItem
import io.github.ratmux.sdhelper.ui.designapi.DeckDrawerShell
import io.github.ratmux.sdhelper.ui.designapi.DeckFeatureCard
import io.github.ratmux.sdhelper.ui.designapi.DeckText
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import org.koin.compose.viewmodel.koinViewModel

/**
 * The home frame: a navigation drawer with the modes (Stream Deck now, Touch Pad and Keyboard marked "coming soon"
 * and not reacting to touch), the connected computer at its top and the settings at its bottom. The content is
 * that of the mode last used.
 */
@Composable
fun HomeScreen(
    onStartDeck: () -> Unit,
    onEditDeck: () -> Unit,
    onOpenComputers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comingSoon = stringResource(R.string.home_coming_soon)
    val drawer =
        DeckDrawer(
            brand = stringResource(R.string.home_brand),
            computerTitle = state.computerName ?: stringResource(R.string.home_connect_computer),
            computerSubtitle = state.computerAddress,
            sectionLabel = stringResource(R.string.home_controls),
            items =
                AppMode.entries.map { mode ->
                    DeckDrawerItem(
                        id = mode.name,
                        label = stringResource(mode.labelRes()),
                        iconKey = mode.iconKey(),
                        selected = mode == state.mode,
                        badge = if (mode.available) null else comingSoon,
                    )
                },
            settingsDescription = stringResource(R.string.home_settings),
        )

    DeckDrawerShell(
        title = stringResource(state.mode.labelRes()),
        drawer = drawer,
        onItemClick = { id -> AppMode.entries.firstOrNull { it.name == id }?.let(viewModel::selectMode) },
        onComputerClick = onOpenComputers,
        onSettingsClick = onOpenSettings,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .padding(
                    padding,
                ).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        ) {
            when (state.mode) {
                AppMode.STREAM_DECK -> StreamDeckHome(onStartDeck, onEditDeck)

                // not reachable while these modes are not available; the drawer does not let them be selected
                AppMode.TOUCH_PAD, AppMode.KEYBOARD -> Unit
            }
        }
    }
}

@Composable
private fun StreamDeckHome(
    onStartDeck: () -> Unit,
    onEditDeck: () -> Unit,
) {
    DeckText(stringResource(R.string.home_eyebrow), style = DeckTextStyle.EYEBROW)
    DeckText(stringResource(R.string.home_headline), style = DeckTextStyle.HEADLINE)
    DeckText(
        stringResource(R.string.home_supporting),
        style = DeckTextStyle.SUPPORTING,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
    )
    DeckFeatureCard(
        iconKey = "play_pause",
        title = stringResource(R.string.home_start_title),
        copy = stringResource(R.string.home_start_copy),
        actionText = stringResource(R.string.home_start_action),
        onAction = onStartDeck,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    DeckFeatureCard(
        iconKey = "settings",
        title = stringResource(R.string.home_edit_title),
        copy = stringResource(R.string.home_edit_copy),
        actionText = stringResource(R.string.home_edit_action),
        onAction = onEditDeck,
    )
}

private fun AppMode.labelRes(): Int =
    when (this) {
        AppMode.STREAM_DECK -> R.string.mode_stream_deck
        AppMode.TOUCH_PAD -> R.string.mode_touch_pad
        AppMode.KEYBOARD -> R.string.mode_keyboard
    }

private fun AppMode.iconKey(): String =
    when (this) {
        AppMode.STREAM_DECK -> "apps"
        AppMode.TOUCH_PAD -> "touchpad"
        AppMode.KEYBOARD -> "keyboard"
    }
