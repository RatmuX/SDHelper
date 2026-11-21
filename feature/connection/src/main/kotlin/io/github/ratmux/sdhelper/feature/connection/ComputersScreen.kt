package io.github.ratmux.sdhelper.feature.connection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.FailureReason
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.ui.designapi.DeckButton
import io.github.ratmux.sdhelper.ui.designapi.DeckDialog
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckProgress
import io.github.ratmux.sdhelper.ui.designapi.DeckRowEnd
import io.github.ratmux.sdhelper.ui.designapi.DeckScaffold
import io.github.ratmux.sdhelper.ui.designapi.DeckSettingsGroup
import io.github.ratmux.sdhelper.ui.designapi.DeckSettingsRow
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckText
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import org.koin.compose.viewmodel.koinViewModel

/** Paired computers, the connection state, and computers found on the network. */
@Composable
fun ComputersScreen(
    onBack: () -> Unit,
    onAddComputer: (host: String, port: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComputersViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }
    val askPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { viewModel.onPermissionResult() }

    DeckScaffold(title = stringResource(R.string.computers_title), modifier = modifier, onBack = onBack) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(
                        padding,
                    ).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.localNetworkGranted) {
                DeckStatusMessage(
                    stringResource(R.string.permission_message),
                    Modifier.fillMaxWidth(),
                    DeckTone.WARNING,
                )
                DeckButton(
                    text = stringResource(R.string.permission_allow),
                    onClick = { askPermission.launch(LocalNetworkAccess.PERMISSION) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.active != null) ConnectionStatusMessage(state.connection)

            DeckText(
                stringResource(R.string.computers_paired),
                style = DeckTextStyle.SECTION,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            if (state.computers.isEmpty()) {
                DeckText(
                    stringResource(R.string.computers_none),
                    style = DeckTextStyle.SUPPORTING,
                    modifier = Modifier.padding(start = 16.dp),
                )
            } else {
                DeckSettingsGroup {
                    state.computers.forEach { computer ->
                        DeckSettingsRow(
                            title = computer.name,
                            summary = "${computer.host}:${computer.port}",
                            iconKey = "computer",
                            end = if (computer.agentId == state.activeAgentId) DeckRowEnd.Check else DeckRowEnd.None,
                            onClick = { viewModel.select(computer.agentId) },
                        )
                    }
                }
            }
            if (state.active != null) {
                DeckButton(
                    text = stringResource(R.string.computers_remove),
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = DeckEmphasis.SECONDARY,
                )
            }

            if (state.discovered.isNotEmpty()) {
                DeckText(
                    stringResource(R.string.computers_found),
                    style = DeckTextStyle.SECTION,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                DeckSettingsGroup {
                    state.discovered.forEach { found ->
                        DeckSettingsRow(
                            title = found.name,
                            summary = "${found.host}:${found.port}",
                            iconKey = "computer",
                            end = DeckRowEnd.Chevron,
                            onClick = { onAddComputer(found.host, found.port) },
                        )
                    }
                }
            }

            DeckButton(
                text = stringResource(R.string.computers_add),
                onClick = { onAddComputer("", 0) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.active?.takeIf { confirmRemove }?.let { computer ->
        DeckDialog(
            title = stringResource(R.string.computers_remove_title),
            text = stringResource(R.string.computers_remove_message, computer.name),
            confirmText = stringResource(R.string.computers_remove_confirm),
            onConfirm = {
                confirmRemove = false
                viewModel.remove(computer)
            },
            onDismiss = { confirmRemove = false },
            dismissText = stringResource(R.string.common_cancel),
        )
    }
}

@Composable
private fun ConnectionStatusMessage(connection: ConnectionState) {
    when (connection) {
        ConnectionState.Disconnected -> {
            DeckStatusMessage(stringResource(R.string.state_disconnected), Modifier.fillMaxWidth())
        }

        is ConnectionState.Connecting -> {
            val text =
                if (connection.attempt <= 1) {
                    stringResource(R.string.state_connecting)
                } else {
                    stringResource(R.string.state_connecting_retry, connection.attempt)
                }
            DeckStatusMessage(text, Modifier.fillMaxWidth())
            DeckProgress(Modifier.fillMaxWidth())
        }

        ConnectionState.Authenticating -> {
            DeckStatusMessage(stringResource(R.string.state_authenticating), Modifier.fillMaxWidth())
        }

        is ConnectionState.Connected -> {
            DeckStatusMessage(
                stringResource(R.string.state_connected, connection.computerName),
                Modifier.fillMaxWidth(),
                DeckTone.POSITIVE,
            )
        }

        is ConnectionState.Failed -> {
            DeckStatusMessage(
                stringResource(
                    when (connection.reason) {
                        FailureReason.CERTIFICATE_MISMATCH -> R.string.state_failed_certificate
                        FailureReason.REVOKED -> R.string.state_failed_revoked
                        FailureReason.PROTOCOL -> R.string.state_failed_protocol
                    },
                ),
                Modifier.fillMaxWidth(),
                DeckTone.ERROR,
            )
        }
    }
}
