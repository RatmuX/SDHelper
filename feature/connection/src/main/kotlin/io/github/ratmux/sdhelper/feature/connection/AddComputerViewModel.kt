package io.github.ratmux.sdhelper.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import io.github.ratmux.sdhelper.core.model.PairingClient
import io.github.ratmux.sdhelper.core.model.PairingFailure
import io.github.ratmux.sdhelper.core.model.PairingOutcome
import io.github.ratmux.sdhelper.core.protocol.PairingUri
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AddStatus {
    data object Idle : AddStatus

    data object Working : AddStatus

    /** PIN flow: the person must confirm at the PC; [code] must match the code shown there. */
    data class WaitingForApproval(
        val code: String,
    ) : AddStatus

    data class Failed(
        val reason: PairingFailure,
    ) : AddStatus

    /** The scanned QR code is not an SDHelper pairing code. */
    data object InvalidCode : AddStatus

    data object Done : AddStatus
}

data class AddComputerUiState(
    val host: String = "",
    val port: String = DEFAULT_PORT.toString(),
    val pin: String = "",
    val status: AddStatus = AddStatus.Idle,
    val localNetworkGranted: Boolean = true,
) {
    val busy: Boolean get() = status is AddStatus.Working || status is AddStatus.WaitingForApproval
    val canSubmitPin: Boolean
        get() = !busy && localNetworkGranted && host.isNotBlank() && portNumber != null && pin.length == PIN_LENGTH
    val portNumber: Int? get() = port.toIntOrNull()?.takeIf { it in 1..MAX_PORT }

    companion object {
        const val DEFAULT_PORT = 47821
        const val PIN_LENGTH = 6
        private const val MAX_PORT = 65535
    }
}

class AddComputerViewModel(
    private val pairing: PairingClient,
    private val store: PairedComputerStore,
    private val network: LocalNetworkAccess,
) : ViewModel() {
    private val form = MutableStateFlow(AddComputerUiState())
    private var job: Job? = null

    val state: StateFlow<AddComputerUiState> =
        combine(form, network.granted) { current, granted -> current.copy(localNetworkGranted = granted) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AddComputerUiState())

    /** Fills the form from a discovered computer; only while the host field is still empty. */
    fun prefill(
        host: String,
        port: Int,
    ) {
        if (host.isBlank() || form.value.host.isNotBlank()) return
        form.update { it.copy(host = host, port = port.toString()) }
    }

    fun onHostChange(value: String) = form.update { it.copy(host = value.trim()) }

    fun onPortChange(value: String) = form.update { it.copy(port = value.filter(Char::isDigit).take(PORT_DIGITS)) }

    fun onPinChange(value: String) =
        form.update { it.copy(pin = value.filter(Char::isDigit).take(AddComputerUiState.PIN_LENGTH)) }

    fun onPermissionResult() = network.refresh()

    /** QR flow: [text] is whatever the scanner read. */
    fun onQrScanned(text: String) {
        val uri = PairingUri.parse(text)
        if (uri == null) {
            form.update { it.copy(status = AddStatus.InvalidCode) }
            return
        }
        start { pairing.pairWithQr(uri.host, uri.port, uri.fingerprint, uri.token) }
    }

    fun submitPin() {
        // Read the source of truth, not the shared `state`, which is stale while nobody collects it.
        val current = form.value.copy(localNetworkGranted = network.granted.value)
        val port = current.portNumber ?: return
        if (!current.canSubmitPin) return
        start {
            pairing.pairWithPin(current.host, port, current.pin) { code ->
                form.update { it.copy(status = AddStatus.WaitingForApproval(code)) }
            }
        }
    }

    private fun start(block: suspend () -> PairingOutcome) {
        if (form.value.busy) return
        job?.cancel()
        form.update { it.copy(status = AddStatus.Working) }
        job =
            viewModelScope.launch {
                when (val outcome = block()) {
                    is PairingOutcome.Success -> {
                        store.save(outcome.computer)
                        store.setActive(outcome.computer.agentId)
                        form.update { it.copy(status = AddStatus.Done, pin = "") }
                    }

                    is PairingOutcome.Failure -> {
                        form.update { it.copy(status = AddStatus.Failed(outcome.reason)) }
                    }
                }
            }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val PORT_DIGITS = 5
    }
}
