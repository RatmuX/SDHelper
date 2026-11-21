package io.github.ratmux.sdhelper.core.model

import kotlinx.coroutines.flow.first

/**
 * Management of whole layouts, shared by the editor (switch, save a copy, new, rename) and the settings (also
 * delete). Texts that depend on the phone's language are passed in by the screen that calls. Names are cleaned,
 * kept unique and within the length limit.
 */
class LayoutManager(
    private val profiles: ProfileStore,
    private val newId: () -> String = LayoutIds::random,
) {
    suspend fun activate(profileId: String) = profiles.setActive(profileId)

    /** An empty layout with one page. It becomes the active layout. */
    suspend fun createEmpty(
        name: String,
        fallbackName: String,
        firstPageName: String,
    ) {
        val grid = GridSize.DEFAULT
        val page = DeckPage(newId(), firstPageName, LayoutOps.emptySlots(grid))
        val created = DeckProfile(newId(), uniqueName(LayoutOps.cleanName(name) ?: fallbackName), grid, listOf(page))
        profiles.save(created)
        profiles.setActive(created.id)
    }

    /** A ready-made layout in [language] under [name] (its own title if blank). It becomes the active layout. */
    suspend fun addFromPreset(
        presetId: String,
        name: String,
        language: String,
    ) {
        val preset = LayoutPresets.byId(presetId) ?: return
        val created = preset.create(language, newId = newId)
        val named = created.copy(name = uniqueName(LayoutOps.cleanName(name) ?: created.name))
        profiles.save(named)
        profiles.setActive(named.id)
    }

    /** A copy under [name] (the original's name with a number if blank). The active layout does not change. */
    suspend fun saveCopy(
        profileId: String,
        name: String,
    ) {
        val original = profiles.profiles.first().firstOrNull { it.id == profileId } ?: return
        profiles.save(
            original.copy(
                id = newId(),
                name = uniqueName(LayoutOps.cleanName(name) ?: original.name),
                pages = original.pages.map { it.copy(id = newId()) },
            ),
        )
    }

    suspend fun rename(
        profileId: String,
        name: String,
    ) {
        val original = profiles.profiles.first().firstOrNull { it.id == profileId } ?: return
        val renamed = LayoutOps.renameProfile(original, name)
        if (renamed != original) profiles.save(renamed)
    }

    /** False when the layout is the last one, which can never be deleted. */
    suspend fun delete(profileId: String): Boolean = profiles.delete(profileId)

    /** `Gaming`, then `Gaming (2)`, `Gaming (3)`, ... and always within the name length limit. */
    private suspend fun uniqueName(base: String): String {
        val taken =
            profiles.profiles
                .first()
                .map { it.name }
                .toSet()
        if (base !in taken) return base
        var number = 2
        while (true) {
            val suffix = " ($number)"
            val candidate = base.take(LayoutLimits.MAX_NAME_LENGTH - suffix.length) + suffix
            if (candidate !in taken) return candidate
            number++
        }
    }
}
