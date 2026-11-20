package io.github.ratmux.sdhelper.data.profiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ratmux.sdhelper.core.model.DeckPage
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.GridSize
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Layouts as one versioned JSON document in Preferences DataStore. Profiles are small, so a document store is enough
 * (ADR-0012). Reading is forgiving: a damaged or older document is repaired as far as possible instead of crashing,
 * and unknown slot types (from a newer app version) read as empty cells.
 */
class DataStoreProfileStore(
    private val dataStore: DataStore<Preferences>,
) : ProfileStore {
    @Serializable
    internal data class StoredSlot(
        val type: String = TYPE_ACTION,
        val actionId: String? = null,
    )

    @Serializable
    internal data class StoredPage(
        val id: String,
        val name: String,
        val slots: List<StoredSlot?> = emptyList(),
    )

    @Serializable
    internal data class StoredProfile(
        val id: String,
        val name: String,
        val columns: Int = GridSize.DEFAULT.columns,
        val rows: Int = GridSize.DEFAULT.rows,
        val pages: List<StoredPage> = emptyList(),
    )

    @Serializable
    internal data class StoredDocument(
        val version: Int = VERSION,
        val profiles: List<StoredProfile> = emptyList(),
    )

    override val profiles: Flow<List<DeckProfile>> = dataStore.data.map { read(it[PROFILES]) }

    override val activeProfileId: Flow<String?> = dataStore.data.map { it[ACTIVE] }

    override suspend fun save(profile: DeckProfile) {
        dataStore.edit { prefs ->
            val existing = read(prefs[PROFILES])
            // a profile that already exists keeps its position in the list
            val updated =
                if (existing.any { it.id == profile.id }) {
                    existing.map { if (it.id == profile.id) profile else it }
                } else {
                    existing + profile
                }
            prefs[PROFILES] = write(updated)
            if (updated.none { it.id == prefs[ACTIVE] }) prefs[ACTIVE] = profile.id
        }
    }

    override suspend fun delete(profileId: String): Boolean {
        var deleted = false
        dataStore.edit { prefs ->
            val current = read(prefs[PROFILES])
            if (current.size <= 1 || current.none { it.id == profileId }) return@edit
            val remaining = current.filterNot { it.id == profileId }
            prefs[PROFILES] = write(remaining)
            if (prefs[ACTIVE] == profileId ||
                remaining.none { it.id == prefs[ACTIVE] }
            ) {
                prefs[ACTIVE] = remaining.first().id
            }
            deleted = true
        }
        return deleted
    }

    override suspend fun setActive(profileId: String) {
        dataStore.edit { prefs ->
            if (read(prefs[PROFILES]).any { it.id == profileId }) prefs[ACTIVE] = profileId
        }
    }

    private fun write(profiles: List<DeckProfile>): String =
        json.encodeToString(
            StoredDocument.serializer(),
            StoredDocument(
                profiles =
                    profiles.map { profile ->
                        StoredProfile(
                            id = profile.id,
                            name = profile.name,
                            columns = profile.grid.columns,
                            rows = profile.grid.rows,
                            pages =
                                profile.pages.map { page ->
                                    StoredPage(
                                        page.id,
                                        page.name,
                                        page.slots.map { slot ->
                                            when (slot) {
                                                is DeckSlot.Action -> StoredSlot(TYPE_ACTION, slot.actionId)
                                                null -> null
                                            }
                                        },
                                    )
                                },
                        )
                    },
            ),
        )

    companion object {
        internal const val VERSION = 1
        internal const val TYPE_ACTION = "action"
        private val PROFILES = stringPreferencesKey("profiles_json")
        private val ACTIVE = stringPreferencesKey("active_profile")

        // defaults are written too: the version number and slot types must be explicit in the stored document
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun create(context: Context): ProfileStore =
            DataStoreProfileStore(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("deck_profiles") },
            )

        /** Parses and repairs a stored document; unreadable input gives an empty list. */
        internal fun read(text: String?): List<DeckProfile> {
            val document =
                text?.let { runCatching { json.decodeFromString(StoredDocument.serializer(), it) }.getOrNull() }
                    ?: return emptyList()
            return document.profiles.mapNotNull { runCatching { repair(it) }.getOrNull() }
        }

        private fun repair(stored: StoredProfile): DeckProfile {
            val grid = GridSize(stored.columns, stored.rows).takeIf { it in GridSize.ALLOWED } ?: GridSize.DEFAULT
            val pages =
                stored.pages
                    .take(LayoutLimits.MAX_PAGES)
                    .map { page ->
                        val slots =
                            List(grid.capacity) { index ->
                                val slot = page.slots.getOrNull(index)
                                if (slot?.type == TYPE_ACTION &&
                                    !slot.actionId.isNullOrBlank()
                                ) {
                                    DeckSlot.Action(slot.actionId)
                                } else {
                                    null
                                }
                            }
                        DeckPage(page.id, LayoutOps.cleanName(page.name) ?: "Page", slots)
                    }.ifEmpty { listOf(DeckPage("page-1", "Page 1", LayoutOps.emptySlots(grid))) }
            return DeckProfile(stored.id, LayoutOps.cleanName(stored.name) ?: "Layout", grid, pages)
        }
    }
}
