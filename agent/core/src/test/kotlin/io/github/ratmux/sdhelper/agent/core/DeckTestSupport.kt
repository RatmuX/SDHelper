package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.pluginapi.ActionDescriptor
import io.github.ratmux.sdhelper.core.pluginapi.ActionProvider
import io.github.ratmux.sdhelper.core.pluginapi.ActionResult
import io.github.ratmux.sdhelper.core.pluginapi.ActionState
import io.github.ratmux.sdhelper.core.protocol.Envelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A provider whose state and behaviour the test controls. */
class FakeProvider(
    override val providerId: String = "fake",
    private val descriptors: List<ActionDescriptor> =
        listOf(
            ActionDescriptor("go", mapOf("en" to "Go", "tr" to "Git"), "icon_go", toggle = true),
            ActionDescriptor("stop", mapOf("en" to "Stop")),
        ),
) : ActionProvider {
    val state = MutableStateFlow<Map<String, ActionState>>(emptyMap())
    val invoked = mutableListOf<Pair<String, Map<String, String>>>()
    var behaviour: suspend (String, Map<String, String>) -> ActionResult = { _, _ -> ActionResult.Success }

    override fun actions(): List<ActionDescriptor> = descriptors

    override val states: Flow<Map<String, ActionState>> get() = state

    override suspend fun invoke(
        actionId: String,
        params: Map<String, String>,
    ): ActionResult {
        invoked += actionId to params
        return behaviour(actionId, params)
    }
}

/** Collects what is pushed to a session. */
class RecordingSink : SessionSink {
    val received = mutableListOf<Envelope>()
    var accepts = true

    override fun trySend(message: Envelope): Boolean {
        if (accepts) received += message
        return accepts
    }
}
