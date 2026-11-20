package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.agent.core.platform.InMemoryAudioController
import io.github.ratmux.sdhelper.agent.core.platform.InMemoryMediaController
import io.github.ratmux.sdhelper.core.model.BuiltinActions
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.plugins.builtinmedia.MediaProvider
import io.github.ratmux.sdhelper.plugins.builtinsystemaudio.SystemAudioProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The layouts live on the phone and name actions by id; the actions live in the agent. This keeps the two in step:
 * if an action is renamed or removed in a built-in plugin, this fails instead of silently breaking the presets.
 */
class PresetsMatchBuiltinActionsTest {
    private val published: Set<String> =
        listOf(SystemAudioProvider(InMemoryAudioController()), MediaProvider(InMemoryMediaController()))
            .flatMap { provider -> provider.actions().map { "${provider.providerId}/${it.id}" } }
            .toSet()

    @Test
    fun `every action a preset uses is published by a built-in provider`() {
        for (preset in LayoutPresets.all) {
            val missing = preset.actionIds.filterNot { it in published }
            assertTrue(missing.isEmpty(), "${preset.id} uses unknown actions: $missing")
        }
    }

    @Test
    fun `the constants of the phone cover exactly the built-in actions`() {
        val constants =
            setOf(
                BuiltinActions.VOLUME_UP,
                BuiltinActions.VOLUME_DOWN,
                BuiltinActions.MUTE_OUTPUT,
                BuiltinActions.MUTE_MICROPHONE,
                BuiltinActions.PLAY_PAUSE,
                BuiltinActions.NEXT,
                BuiltinActions.PREVIOUS,
            )
        assertEquals(published, constants)
    }
}
