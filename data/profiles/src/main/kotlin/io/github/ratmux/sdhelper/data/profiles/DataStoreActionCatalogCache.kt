package io.github.ratmux.sdhelper.data.profiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ratmux.sdhelper.core.model.ActionCatalogCache
import io.github.ratmux.sdhelper.core.model.DeckAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Remembers the actions of computers the phone has talked to, so layouts keep their titles and icons offline. */
class DataStoreActionCatalogCache(
    private val dataStore: DataStore<Preferences>,
) : ActionCatalogCache {
    @Serializable
    private data class Stored(
        val id: String,
        val title: String,
        val icon: String? = null,
        val toggle: Boolean = false,
    )

    override val actions: Flow<List<DeckAction>> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun update(actions: List<DeckAction>) {
        if (actions.isEmpty()) return
        dataStore.edit { prefs ->
            val known = decode(prefs[KEY]).associateBy { it.id } + actions.associateBy { it.id }
            prefs[KEY] =
                json.encodeToString(LIST, known.values.map { Stored(it.id, it.title, it.icon, it.toggle) })
        }
    }

    private fun decode(text: String?): List<DeckAction> =
        text
            ?.let { runCatching { json.decodeFromString(LIST, it) }.getOrNull() }
            .orEmpty()
            .map { DeckAction(it.id, it.title, it.icon, it.toggle) }

    companion object {
        private val KEY = stringPreferencesKey("catalog_json")
        private val json = Json { ignoreUnknownKeys = true }
        private val LIST = ListSerializer(Stored.serializer())

        fun create(context: Context): ActionCatalogCache =
            DataStoreActionCatalogCache(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("action_catalog") },
            )
    }
}
