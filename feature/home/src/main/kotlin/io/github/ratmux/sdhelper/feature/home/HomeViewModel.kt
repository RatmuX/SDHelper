package io.github.ratmux.sdhelper.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.AppMode
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import io.github.ratmux.sdhelper.core.model.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    /** The mode shown, restored from the last use. */
    val mode: AppMode = AppMode.STREAM_DECK,
    /** Name and address of the connected computer; both null while no computer is connected. */
    val computerName: String? = null,
    val computerAddress: String? = null,
)

/** The home frame: the current mode and the connected computer shown at the top of the drawer. */
class HomeViewModel(
    private val settings: SettingsStore,
    status: ConnectionStatus,
    computers: PairedComputerStore,
) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(
            settings.settings,
            status.state,
            computers.computers,
            computers.activeAgentId,
        ) { saved, connection, all, activeId ->
            val connected = connection as? ConnectionState.Connected
            HomeUiState(
                // a mode that is not available (for example one stored by a newer version) opens the Stream Deck
                mode = saved.lastMode.takeIf { it.available } ?: AppMode.STREAM_DECK,
                computerName = connected?.computerName,
                computerAddress =
                    connected?.let {
                        (
                            all.firstOrNull { it.agentId == activeId }
                                ?: all.firstOrNull { it.name == connected.computerName }
                        )?.host
                    },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    /** Switches the mode and remembers it; a mode that is not available is ignored. */
    fun selectMode(mode: AppMode) {
        if (!mode.available) return
        viewModelScope.launch { settings.update { it.copy(lastMode = mode) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
