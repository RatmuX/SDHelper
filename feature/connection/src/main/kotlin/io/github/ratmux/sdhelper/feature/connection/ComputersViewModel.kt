package io.github.ratmux.sdhelper.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ratmux.sdhelper.core.model.ComputerDiscovery
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DiscoveredComputer
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import io.github.ratmux.sdhelper.core.model.PairingClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ComputersUiState(
    val computers: List<PairedComputer> = emptyList(),
    /** Computers announced on the network that are not paired yet. */
    val discovered: List<DiscoveredComputer> = emptyList(),
    val activeAgentId: String? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val localNetworkGranted: Boolean = true,
) {
    val active: PairedComputer? get() = computers.firstOrNull { it.agentId == activeAgentId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ComputersViewModel(
    private val store: PairedComputerStore,
    private val pairing: PairingClient,
    private val network: LocalNetworkAccess,
    status: ConnectionStatus,
    discovery: ComputerDiscovery,
) : ViewModel() {
    // Scanning the network needs the permission; without it the list simply stays empty.
    private val discovered =
        network.granted.flatMapLatest { granted ->
            if (granted) discovery.discover() else flowOf(emptyList())
        }

    val state: StateFlow<ComputersUiState> =
        combine(store.computers, store.activeAgentId, status.state, network.granted, discovered) {
            computers,
            active,
            connection,
            granted,
            found,
            ->
            ComputersUiState(
                computers = computers,
                discovered = found.filter { seen -> computers.none { it.agentId == seen.agentId } },
                activeAgentId = active,
                connection = connection,
                localNetworkGranted = granted,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ComputersUiState())

    fun select(agentId: String) {
        viewModelScope.launch { store.setActive(agentId) }
    }

    fun remove(computer: PairedComputer) {
        viewModelScope.launch {
            store.remove(computer.agentId)
            pairing.forget(computer)
        }
    }

    /** Call after the permission request was answered. */
    fun onPermissionResult() = network.refresh()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
