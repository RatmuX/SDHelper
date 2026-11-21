package io.github.ratmux.sdhelper.feature.deck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.ui.components.DeckGridPage
import io.github.ratmux.sdhelper.ui.components.ImmersiveLandscape
import io.github.ratmux.sdhelper.ui.components.toTileTone
import io.github.ratmux.sdhelper.ui.designapi.DeckBackground
import io.github.ratmux.sdhelper.ui.designapi.DeckButtonTile
import io.github.ratmux.sdhelper.ui.designapi.DeckPageIndicator
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private const val MESSAGE_MILLIS = 4_000L

/**
 * The control panel: landscape, full screen, no bars. Large square buttons, swipe left and right between the pages
 * of the active layout, and at the bottom the page dots with the page name. There is deliberately no way to edit
 * from here; editing lives in its own screen so a stray touch can never change the layout. A short message appears
 * on top only when something needs saying (not connected, or the computer refused an action).
 */
@Composable
fun DeckScreen(
    modifier: Modifier = Modifier,
    viewModel: DeckViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImmersiveLandscape(keepScreenOn = state.settings.keepScreenOn)

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MILLIS)
            viewModel.dismissMessage()
        }
    }

    DeckBackground(modifier) {
        val profile = state.profile ?: return@DeckBackground
        // a new pager (and so the first page) for each layout; the page survives rotation and recreation
        key(profile.id) {
            val pager = rememberPagerState { profile.pages.size }
            val haptic = LocalHapticFeedback.current
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        pageSpacing = 16.dp,
                    ) { pageIndex ->
                        val page = profile.pages[pageIndex]
                        DeckGridPage(columns = profile.grid.columns, rows = profile.grid.rows) { index, cell ->
                            val slot = page.slots.getOrNull(index)
                            val button = (slot as? DeckSlot.Action)?.let { state.buttons[it.actionId] }
                            if (button != null) {
                                DeckButtonTile(
                                    title = button.title,
                                    modifier = cell,
                                    iconKey = button.iconKey,
                                    toggled = button.state.toggled,
                                    label = button.state.label,
                                    tone = button.state.tone.toTileTone(),
                                    enabled = button.available,
                                    onClick = {
                                        if (state.settings.hapticFeedback) {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        }
                                        viewModel.press(button.actionId)
                                    },
                                )
                            }
                        }
                    }
                    val pageName =
                        profile.pages
                            .getOrNull(pager.currentPage)
                            ?.name
                            .orEmpty()
                    DeckPageIndicator(
                        count = profile.pages.size,
                        current = pager.currentPage,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp),
                        label =
                            stringResource(
                                R.string.deck_page_label,
                                pageName,
                                pager.currentPage + 1,
                                profile.pages.size,
                            ),
                    )
                }
                Notice(state, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            }
        }
    }
}

/** What needs saying right now: the computer's refusal or the lost connection; nothing otherwise. */
@Composable
private fun Notice(
    state: DeckUiState,
    modifier: Modifier,
) {
    val text =
        when (val notice = state.message) {
            DeckMessage.NotConnected -> {
                stringResource(R.string.deck_message_not_connected)
            }

            is DeckMessage.Refused -> {
                stringResource(R.string.deck_message_refused, notice.message)
            }

            null -> {
                if (state.connection !is ConnectionState.Connected) {
                    stringResource(
                        R.string.deck_not_connected,
                    )
                } else {
                    null
                }
            }
        } ?: return
    DeckStatusMessage(
        text = text,
        modifier = modifier,
        tone = if (state.message != null) DeckTone.ERROR else DeckTone.NEUTRAL,
    )
}
