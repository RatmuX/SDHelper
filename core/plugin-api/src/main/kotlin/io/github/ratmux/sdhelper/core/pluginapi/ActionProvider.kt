package io.github.ratmux.sdhelper.core.pluginapi

/** Result of running an action. */
sealed interface ActionResult {
    data object Success : ActionResult

    data class Failure(
        val message: String,
    ) : ActionResult
}

/** One action a provider offers; parameter schemas arrive with the plugin system (Phase 4). */
data class ActionDescriptor(
    val id: String,
    val title: String,
    val toggle: Boolean = false,
)

/**
 * The single interface behind every action: built-in actions and third-party plugins implement it
 * (external plugins through an adapter). See docs/PLUGIN_SPEC.md.
 */
interface ActionProvider {
    val providerId: String

    fun actions(): List<ActionDescriptor>

    suspend fun invoke(
        actionId: String,
        params: Map<String, String>,
    ): ActionResult
}
