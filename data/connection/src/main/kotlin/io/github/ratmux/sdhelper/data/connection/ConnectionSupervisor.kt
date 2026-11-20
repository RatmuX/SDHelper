package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.core.model.ComputerDiscovery
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Decides when to be connected: only while the app is in the foreground, a computer is selected and the local
 * network permission is granted. It also follows a paired computer to a new address announced via mDNS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSupervisor(
    scope: CoroutineScope,
    private val store: PairedComputerStore,
    private val manager: DefaultConnectionManager,
    discovery: ComputerDiscovery,
    localNetwork: LocalNetworkAccess,
) {
    private val foreground = MutableStateFlow(false)

    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    init {
        scope.launch {
            combine(
                store.computers,
                store.activeAgentId,
                foreground,
                localNetwork.granted,
            ) { computers, active, visible, granted ->
                computers.firstOrNull { it.agentId == active }.takeIf { visible && granted }
            }.distinctUntilChanged()
                .collect { target -> if (target == null) manager.disconnect() else manager.connect(target) }
        }
        scope.launch {
            combine(foreground, localNetwork.granted) { visible, granted -> visible && granted }
                .distinctUntilChanged()
                .flatMapLatest { active -> if (active) discovery.discover() else emptyFlow() }
                .collect { found ->
                    val known = store.computers.first()
                    found.forEach { seen ->
                        known
                            .firstOrNull {
                                it.agentId == seen.agentId && (it.host != seen.host || it.port != seen.port)
                            }?.let { store.save(it.copy(host = seen.host, port = seen.port)) }
                    }
                }
        }
    }
}
