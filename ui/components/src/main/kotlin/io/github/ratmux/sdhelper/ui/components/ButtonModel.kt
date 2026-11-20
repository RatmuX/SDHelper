package io.github.ratmux.sdhelper.ui.components

import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActionTone
import io.github.ratmux.sdhelper.ui.designapi.DeckTileTone

/** Everything a button needs to be drawn, resolved from the layout, the cached catalog and the live state. */
data class ButtonModel(
    val actionId: String,
    val title: String,
    val iconKey: String?,
    val toggle: Boolean,
    val state: DeckActionState,
    /** True only while connected, the computer still offers the action, and the action says it can work. */
    val available: Boolean,
    /** False when the phone has never seen this action (for example a layout from a preset for another computer). */
    val known: Boolean,
)

object ButtonModels {
    /**
     * @param known actions seen before, by id (the persistent cache)
     * @param liveIds ids the connected computer offers right now
     * @param states live state by id
     */
    fun build(
        actionId: String,
        known: Map<String, DeckAction>,
        states: Map<String, DeckActionState>,
        liveIds: Set<String>,
        connected: Boolean,
    ): ButtonModel {
        val action = known[actionId]
        val state = states[actionId] ?: DeckActionState()
        return ButtonModel(
            actionId = actionId,
            title = action?.title ?: fallbackTitle(actionId),
            iconKey = action?.icon,
            toggle = action?.toggle ?: false,
            state = state,
            available = connected && actionId in liveIds && state.available,
            known = action != null,
        )
    }

    /** `builtin.media/play-pause` becomes `Play pause`, so an unknown action still reads sensibly. */
    fun fallbackTitle(actionId: String): String =
        actionId
            .substringAfterLast('/')
            .replace('-', ' ')
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
}

fun DeckActionTone.toTileTone(): DeckTileTone =
    when (this) {
        DeckActionTone.NORMAL -> DeckTileTone.NORMAL
        DeckActionTone.ACTIVE -> DeckTileTone.ACTIVE
        DeckActionTone.WARNING -> DeckTileTone.WARNING
        DeckActionTone.ALERT -> DeckTileTone.ALERT
    }
