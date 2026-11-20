package io.github.ratmux.sdhelper.data.profiles

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ratmux.sdhelper.core.model.DeckAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreActionCatalogCacheTest {
    private val file = createTempDirectory("sdhelper-catalog").resolve("catalog.preferences_pb").toFile()
    private val dataStore = PreferenceDataStoreFactory.create { file }

    @Test
    fun `actions are remembered across instances`() =
        runTest {
            DataStoreActionCatalogCache(dataStore).update(listOf(DeckAction("a/x", "X", "icon_x", true)))
            val loaded = DataStoreActionCatalogCache(dataStore).actions.first()
            assertEquals(listOf(DeckAction("a/x", "X", "icon_x", true)), loaded)
        }

    @Test
    fun `an update merges - new actions are added, known ones refreshed, others kept`() =
        runTest {
            val cache = DataStoreActionCatalogCache(dataStore)
            cache.update(listOf(DeckAction("a/1", "One", null, false), DeckAction("a/2", "Two", null, false)))
            cache.update(listOf(DeckAction("a/2", "Zwei", null, false), DeckAction("b/3", "Three", null, false)))

            val byId = cache.actions.first().associateBy { it.id }
            assertEquals(setOf("a/1", "a/2", "b/3"), byId.keys)
            assertEquals("Zwei", byId.getValue("a/2").title)
        }

    @Test
    fun `an empty update changes nothing and damaged data reads as empty`() =
        runTest {
            val cache = DataStoreActionCatalogCache(dataStore)
            cache.update(listOf(DeckAction("a/1", "One", null, false)))
            cache.update(emptyList())
            assertEquals(1, cache.actions.first().size)

            dataStore.edit { it[stringPreferencesKey("catalog_json")] = "garbage" }
            assertEquals(emptyList(), cache.actions.first())
        }
}
