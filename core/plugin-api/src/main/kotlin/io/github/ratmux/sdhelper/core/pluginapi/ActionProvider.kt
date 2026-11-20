package io.github.ratmux.sdhelper.core.pluginapi

import kotlinx.coroutines.flow.Flow

/** Result of running an action. */
sealed interface ActionResult {
    data object Success : ActionResult

    data class Failure(
        val message: String,
    ) : ActionResult
}

/**
 * How a button should look, as a meaning rather than a color: each design language maps the tone to its own
 * palette (docs/DESIGN_SYSTEM.md).
 */
enum class ActionTone { NORMAL, ACTIVE, WARNING, ALERT }

/** Live state of one action, for example "microphone is muted". */
data class ActionState(
    /** False when the action cannot work right now, for example no microphone is present. */
    val available: Boolean = true,
    /** For toggle actions: whether the toggled state is on. */
    val toggled: Boolean? = null,
    /** Short dynamic text such as a volume percentage or the track that is playing. */
    val label: String? = null,
    val tone: ActionTone = ActionTone.NORMAL,
)

/**
 * One action a provider offers. [id] is local to the provider; the agent publishes it as `providerId/id`.
 * Parameter schemas arrive with the plugin system (Phase 4).
 */
data class ActionDescriptor(
    val id: String,
    /** Titles by language code; clients fall back to `en`. */
    val title: Map<String, String>,
    /** Key of an icon in the built-in icon set. */
    val icon: String? = null,
    val toggle: Boolean = false,
)

/**
 * The single interface behind every action: built-in actions and third-party plugins implement it
 * (external plugins through an adapter). See docs/PLUGIN_SPEC.md.
 */
interface ActionProvider {
    val providerId: String

    fun actions(): List<ActionDescriptor>

    /** State of this provider's actions by local action id; emits the current state first, then every change. */
    val states: Flow<Map<String, ActionState>>

    suspend fun invoke(
        actionId: String,
        params: Map<String, String>,
    ): ActionResult
}
