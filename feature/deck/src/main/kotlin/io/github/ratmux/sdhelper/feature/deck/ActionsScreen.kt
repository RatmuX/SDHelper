package io.github.ratmux.sdhelper.feature.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.ui.designapi.DeckButton
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckListItem
import io.github.ratmux.sdhelper.ui.designapi.DeckProgress
import io.github.ratmux.sdhelper.ui.designapi.DeckScaffold
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import org.koin.compose.viewmodel.koinViewModel

/**
 * The actions of the connected computer as a plain list. This is the temporary control panel of Phase 3b;
 * the button grid of Phase 3c replaces it.
 */
@Composable
fun ActionsScreen(
    onBack: () -> Unit,
    onOpenComputers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DeckScaffold(title = stringResource(R.string.actions_title), modifier = modifier, onBack = onBack) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val connection = state.connection) {
                is ConnectionState.Connected -> {
                    // nothing to announce while everything works: the list below is the content
                }

                is ConnectionState.Failed, ConnectionState.Disconnected -> {
                    DeckStatusMessage(
                        text = stringResource(R.string.actions_not_connected),
                        modifier = Modifier.fillMaxWidth(),
                        tone = DeckTone.WARNING,
                    )
                    DeckButton(
                        text = stringResource(R.string.actions_open_computers),
                        onClick = onOpenComputers,
                        modifier = Modifier.fillMaxWidth(),
                        emphasis = DeckEmphasis.SECONDARY,
                    )
                }

                else -> {
                    val progress =
                        when (connection) {
                            is ConnectionState.Connecting -> R.string.actions_connecting
                            else -> R.string.actions_signing_in
                        }
                    DeckStatusMessage(stringResource(progress), Modifier.fillMaxWidth())
                    DeckProgress(Modifier.fillMaxWidth())
                }
            }

            state.error?.let { error ->
                DeckStatusMessage(
                    text =
                        when (error) {
                            ActionError.NotConnected -> stringResource(R.string.actions_error_not_connected)
                            is ActionError.Refused -> stringResource(R.string.actions_error_refused, error.message)
                        },
                    modifier = Modifier.fillMaxWidth(),
                    tone = DeckTone.ERROR,
                )
            }

            if (state.connection is ConnectionState.Connected && state.rows.isEmpty()) {
                DeckStatusMessage(stringResource(R.string.actions_empty), Modifier.fillMaxWidth())
            }

            state.rows.forEach { row ->
                DeckListItem(
                    title = row.action.title,
                    supporting =
                        when {
                            !row.state.available -> stringResource(R.string.actions_unavailable)
                            else -> row.state.label
                        },
                    trailing =
                        row.state.toggled?.let { on ->
                            stringResource(if (on) R.string.actions_on else R.string.actions_off)
                        },
                    enabled = row.state.available,
                    onClick = {
                        viewModel.dismissError()
                        viewModel.invoke(row)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
