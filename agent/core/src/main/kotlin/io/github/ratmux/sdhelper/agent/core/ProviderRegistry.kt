package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.pluginapi.ActionDescriptor
import io.github.ratmux.sdhelper.core.pluginapi.ActionProvider
import io.github.ratmux.sdhelper.core.pluginapi.ActionState
import io.github.ratmux.sdhelper.core.protocol.DeckActionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/** All action providers of the agent. Actions are published as `providerId/actionId`. */
class ProviderRegistry(
    private val providers: List<ActionProvider>,
) {
    /** A published action and who runs it. */
    class Entry(
        val qualifiedId: String,
        val provider: ActionProvider,
        val localId: String,
        val descriptor: ActionDescriptor,
    )

    private val entries: Map<String, Entry>

    init {
        require(providers.map { it.providerId }.toSet().size == providers.size) { "Provider ids must be unique" }
        val all =
            providers.flatMap { provider ->
                provider.actions().map { Entry("${provider.providerId}/${it.id}", provider, it.id, it) }
            }
        require(all.map { it.qualifiedId }.toSet().size == all.size) { "Action ids must be unique within a provider" }
        entries = all.associateBy { it.qualifiedId }
    }

    fun find(qualifiedId: String): Entry? = entries[qualifiedId]

    fun catalog(): List<DeckActionInfo> =
        entries.values.map {
            DeckActionInfo(
                it.qualifiedId,
                it.descriptor.title,
                it.descriptor.icon,
                it.descriptor.toggle,
            )
        }

    /** Latest known state of every published action, updated as providers report changes. */
    fun states(scope: CoroutineScope): StateFlow<Map<String, ActionState>> =
        merge(
            *providers
                .map { provider ->
                    provider.states.map { local ->
                        local.mapKeys { (id, _) -> "${provider.providerId}/$id" }.filterKeys { it in entries }
                    }
                }.toTypedArray(),
        ).runningFold(emptyMap<String, ActionState>()) { known, update -> known + update }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())
}
