package io.github.ratmux.sdhelper

import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Makes sure there are layouts to show: on the first start (or after damaged data was discarded) the starter
 * layouts (default, streaming, meeting, gaming) are created in the phone's language; the first one is active.
 */
class LayoutBootstrap(
    store: ProfileStore,
    scope: CoroutineScope,
    language: String = Locale.getDefault().language,
) {
    init {
        scope.launch {
            if (store.profiles.first().isEmpty()) {
                // saved in this order; the first profile saved becomes the active one
                LayoutPresets.all.forEach { store.save(it.create(language)) }
            }
        }
    }
}
