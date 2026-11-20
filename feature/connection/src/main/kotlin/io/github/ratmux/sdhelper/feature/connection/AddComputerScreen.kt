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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.core.model.PairingFailure
import io.github.ratmux.sdhelper.ui.designapi.DeckButton
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckProgress
import io.github.ratmux.sdhelper.ui.designapi.DeckScaffold
import io.github.ratmux.sdhelper.ui.designapi.DeckStatusMessage
import io.github.ratmux.sdhelper.ui.designapi.DeckText
import io.github.ratmux.sdhelper.ui.designapi.DeckTextField
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import org.koin.compose.viewmodel.koinViewModel

/** Pairing by QR code or by address and PIN. [prefillHost] and [prefillPort] come from a discovered computer. */
@Composable
fun AddComputerScreen(
    prefillHost: String,
    prefillPort: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddComputerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(prefillHost, prefillPort) { viewModel.prefill(prefillHost, prefillPort) }
    LaunchedEffect(state.status) { if (state.status == AddStatus.Done) onDone() }

    val scanPrompt = stringResource(R.string.add_scan_prompt)
    val scanner =
        rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(viewModel::onQrScanned) }
    val askPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { viewModel.onPermissionResult() }

    DeckScaffold(title = stringResource(R.string.add_title), modifier = modifier, onBack = onBack) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
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

            DeckButton(
                text = stringResource(R.string.add_scan),
                onClick = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt(scanPrompt)
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.localNetworkGranted && !state.busy,
            )

            DeckText(stringResource(R.string.add_manual), style = DeckTextStyle.TITLE)
            DeckTextField(
                value = state.host,
                onValueChange = viewModel::onHostChange,
                label = stringResource(R.string.add_host),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            DeckTextField(
                value = state.port,
                onValueChange = viewModel::onPortChange,
                label = stringResource(R.string.add_port),
                modifier = Modifier.fillMaxWidth(),
                numeric = true,
                isError = state.portNumber == null,
                enabled = !state.busy,
            )
            DeckTextField(
                value = state.pin,
                onValueChange = viewModel::onPinChange,
                label = stringResource(R.string.add_pin),
                modifier = Modifier.fillMaxWidth(),
                numeric = true,
                enabled = !state.busy,
            )
            DeckButton(
                text = stringResource(R.string.add_pair),
                onClick = viewModel::submitPin,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmitPin,
                emphasis = DeckEmphasis.SECONDARY,
            )

            StatusArea(state.status)
        }
    }
}

@Composable
private fun StatusArea(status: AddStatus) {
    if (status == AddStatus.Idle) return
    when (status) {
        AddStatus.Idle -> {
            // handled above: nothing to show
        }

        AddStatus.Working -> {
            DeckStatusMessage(stringResource(R.string.add_working), Modifier.fillMaxWidth())
            DeckProgress(Modifier.fillMaxWidth())
        }

        is AddStatus.WaitingForApproval -> {
            DeckStatusMessage(
                stringResource(R.string.add_waiting, status.code),
                Modifier.fillMaxWidth(),
                DeckTone.WARNING,
            )
            DeckProgress(Modifier.fillMaxWidth())
        }

        is AddStatus.Failed -> {
            DeckStatusMessage(
                stringResource(
                    when (status.reason) {
                        PairingFailure.UNREACHABLE -> R.string.add_failed_unreachable
                        PairingFailure.CERTIFICATE_MISMATCH -> R.string.add_failed_certificate
                        PairingFailure.REJECTED -> R.string.add_failed_rejected
                        PairingFailure.DENIED -> R.string.add_failed_denied
                        PairingFailure.LOCKED -> R.string.add_failed_locked
                        PairingFailure.PROTOCOL -> R.string.add_failed_protocol
                    },
                ),
                Modifier.fillMaxWidth(),
                DeckTone.ERROR,
            )
        }

        AddStatus.InvalidCode -> {
            DeckStatusMessage(stringResource(R.string.add_invalid_code), Modifier.fillMaxWidth(), DeckTone.ERROR)
        }

        AddStatus.Done -> {
            DeckStatusMessage(stringResource(R.string.add_done), Modifier.fillMaxWidth(), DeckTone.POSITIVE)
        }
    }
}
