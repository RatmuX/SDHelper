package io.github.ratmux.sdhelper.feature.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.ratmux.sdhelper.ui.designapi.DeckButton
import io.github.ratmux.sdhelper.ui.designapi.DeckButtonTile
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckListItem
import io.github.ratmux.sdhelper.ui.designapi.DeckModal
import io.github.ratmux.sdhelper.ui.designapi.DeckPageIndicator
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckText
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private const val MESSAGE_MILLIS = 4_000L

/**
 * The control panel: landscape, full screen, large square buttons, swipe left and right between the pages of the
 * active layout. There is deliberately no way to edit from here; editing lives in its own tab so a stray touch
 * can never change the layout.
 */
@Composable
fun DeckScreen(
    onOpenComputers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeckViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImmersiveLandscape(keepScreenOn = state.settings.keepScreenOn)

    DeckBackground(modifier) {
        val profile = state.profile
        if (profile == null) return@DeckBackground
        var chooseProfile by remember { mutableStateOf(false) }
        // a new pager (and so the first page) for each layout
        key(profile.id) {
            val pager = rememberPagerState { profile.pages.size }
            val haptic = LocalHapticFeedback.current
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
                Header(
                    state = state,
                    pageName =
                        profile.pages
                            .getOrNull(pager.currentPage)
                            ?.name
                            .orEmpty(),
                    pageCount = profile.pages.size,
                    currentPage = pager.currentPage,
                    onChooseProfile = { chooseProfile = true },
                    onOpenComputers = onOpenComputers,
                    onMessageShown = viewModel::dismissMessage,
                )
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
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.ContextClick,
                                        )
                                    }
                                    viewModel.press(button.actionId)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (chooseProfile) {
            DeckModal(
                title = stringResource(R.string.deck_choose_layout),
                onDismiss = { chooseProfile = false },
                dismissText = stringResource(R.string.deck_close),
            ) {
                state.profiles.forEach { option ->
                    DeckListItem(
                        title = option.name,
                        supporting = stringResource(R.string.deck_page_count, option.pages.size),
                        trailing = if (option.id == profile.id) stringResource(R.string.deck_active) else null,
                        onClick = {
                            viewModel.selectProfile(option.id)
                            chooseProfile = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: DeckUiState,
    pageName: String,
    pageCount: Int,
    currentPage: Int,
    onChooseProfile: () -> Unit,
    onOpenComputers: () -> Unit,
    onMessageShown: () -> Unit,
) {
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MILLIS)
            onMessageShown()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckButton(
            text = state.profile?.name.orEmpty(),
            onClick = onChooseProfile,
            emphasis = DeckEmphasis.SECONDARY,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            val notice = state.message
            when {
                notice != null -> {
                    DeckStatusMessage(
                        text =
                            when (notice) {
                                DeckMessage.NotConnected -> stringResource(R.string.deck_message_not_connected)
                                is DeckMessage.Refused -> stringResource(R.string.deck_message_refused, notice.message)
                            },
                        tone = DeckTone.ERROR,
                    )
                }

                else -> {
                    DeckText(pageName, style = DeckTextStyle.TITLE)
                }
            }
        }
        if (state.connection !is ConnectionState.Connected) {
            DeckButton(
                text = stringResource(R.string.deck_not_connected),
                onClick = onOpenComputers,
                emphasis = DeckEmphasis.SECONDARY,
            )
        }
        if (pageCount > 1) DeckPageIndicator(count = pageCount, current = currentPage)
    }
}
