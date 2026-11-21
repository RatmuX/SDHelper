package io.github.ratmux.sdhelper.data.profiles

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ratmux.sdhelper.core.model.DeckPage
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.GridSize
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.SlotPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreProfileStoreTest {
    private val file: File = createTempDirectory("sdhelper-profiles").resolve("profiles.preferences_pb").toFile()
    private val dataStore = PreferenceDataStoreFactory.create { file }

    /** A second wrapper over the same DataStore: DataStore allows only one instance per file. */
    private fun newStore() = DataStoreProfileStore(dataStore)

    private fun profile(
        id: String,
        name: String = "Profile $id",
    ) = LayoutPresets.EVERYDAY.create("en", newId = { "$id-${System.nanoTime()}" }).copy(id = id, name = name)

    @Test
    fun `a saved profile comes back complete, also from a new instance`() =
        runTest {
            val original = profile("a")
            DataStoreProfileStore(dataStore).save(original)

            val loaded = newStore().profiles.first().single()
            assertEquals(original, loaded)
        }

    @Test
    fun `the first profile becomes active and later ones do not take over`() =
        runTest {
            val store = DataStoreProfileStore(dataStore)
            store.save(profile("a"))
            store.save(profile("b"))
            assertEquals("a", store.activeProfileId.first())
            store.setActive("b")
            assertEquals("b", store.activeProfileId.first())
        }

    @Test
    fun `saving an existing profile replaces it and keeps its place in the list`() =
        runTest {
            val store = DataStoreProfileStore(dataStore)
            store.save(profile("a"))
            store.save(profile("b"))
            store.save(profile("a", name = "Renamed"))

            assertEquals(listOf("a", "b"), store.profiles.first().map { it.id })
            assertEquals(
                "Renamed",
                store.profiles
                    .first()
                    .first()
                    .name,
            )
        }

    @Test
    fun `the last profile cannot be deleted, and deleting the active one activates another`() =
        runTest {
            val store = DataStoreProfileStore(dataStore)
            store.save(profile("a"))
            assertFalse(store.delete("a"))
            assertEquals(1, store.profiles.first().size)

            store.save(profile("b"))
            assertTrue(store.delete("a"))
            assertEquals("b", store.activeProfileId.first())
            assertFalse(store.delete("missing"))
        }

    @Test
    fun `activating an unknown profile is ignored`() =
        runTest {
            val store = DataStoreProfileStore(dataStore)
            store.save(profile("a"))
            store.setActive("ghost")
            assertEquals("a", store.activeProfileId.first())
        }

    @Test
    fun `damaged data reads as no profiles instead of crashing`() =
        runTest {
            dataStore.edit { it[stringPreferencesKey("profiles_json")] = "{ this is not json" }
            assertEquals(emptyList(), DataStoreProfileStore(dataStore).profiles.first())
            assertNull(DataStoreProfileStore(dataStore).activeProfileId.first())
        }

    @Test
    fun `stored data is repaired - wrong sizes, unknown grid, unknown slot types, no pages`() {
        val damaged =
            """
            {"version":1,"profiles":[
              {"id":"p","name":"  Mine ","columns":9,"rows":9,"pages":[
                {"id":"x","name":"One","slots":[
                  {"type":"action","actionId":"a/b"},
                  {"type":"folder","actionId":"f"},
                  null,
                  {"type":"action"}
                ]}
              ]},
              {"id":"empty","name":"No pages","columns":3,"rows":2,"pages":[]},
              {"id":"future","name":"From a newer app","version":7,"extra":true,"pages":[{"id":"y","name":"Y","slots":[]}]}
            ]}
            """.trimIndent()

        val profiles = DataStoreProfileStore.read(damaged)

        assertEquals(3, profiles.size)
        val first = profiles[0]
        assertEquals("Mine", first.name)
        assertEquals(GridSize.DEFAULT, first.grid, "an unsupported grid falls back to the default")
        assertEquals(8, first.pages[0].slots.size, "slot list is padded to the grid")
        assertEquals(DeckSlot.Action("a/b"), first.slotAt(SlotPosition(0, 0)))
        assertNull(first.slotAt(SlotPosition(0, 1)), "unknown slot types become empty cells")
        assertNull(first.slotAt(SlotPosition(0, 3)), "an action without an id is empty")
        assertEquals(1, profiles[1].pages.size, "a profile always has a page")
        assertEquals(GridSize(3, 2), profiles[1].grid)
    }

    @Test
    fun `too many pages and too many slots are cut down`() {
        val pages =
            (1..12).joinToString(",") {
                """{"id":"p$it","name":"P$it","slots":[${List(
                    30,
                ) { """{"type":"action","actionId":"x/$it"}""" }.joinToString(",")}]}"""
            }
        val profile =
            DataStoreProfileStore
                .read(
                    """{"profiles":[{"id":"big","name":"Big","pages":[$pages]}]}""",
                ).single()
        assertEquals(8, profile.pages.size)
        assertTrue(profile.pages.all { it.slots.size == 8 })
    }

    @Test
    fun `the document carries a version number for future migrations`() =
        runTest {
            DataStoreProfileStore(dataStore).save(profile("a"))
            val json = dataStore.data.first()[stringPreferencesKey("profiles_json")]
            assertTrue(json!!.contains("\"version\":1") || json.contains("\"version\": 1"), json)
        }

    @Test
    fun `edits made with the layout operations survive a save`() =
        runTest {
            val store = DataStoreProfileStore(dataStore)
            val base = profile("a")
            store.save(LayoutOps.addPage(LayoutOps.move(base, SlotPosition(0, 0), SlotPosition(1, 7)), "Extra"))

            val loaded = store.profiles.first().single()
            assertEquals(3, loaded.pages.size)
            assertEquals(base.slotAt(SlotPosition(0, 0)), loaded.slotAt(SlotPosition(1, 7)))
            assertEquals(DeckProfile::class, loaded::class)
            assertEquals(DeckPage::class, loaded.pages[0]::class)
        }

    @Test
    fun `folders and their buttons survive a save and a reload`() =
        runTest {
            val base = profile("a")
            val withFolder =
                LayoutOps.addToFolder(
                    LayoutOps.createFolder(base, SlotPosition(0, 7), "Games", id = "folder-1"),
                    SlotPosition(0, 7),
                    "builtin.media/next",
                )
            DataStoreProfileStore(dataStore).save(withFolder)

            val loaded = newStore().profiles.first().single()
            assertEquals(withFolder, loaded)
            assertEquals(
                DeckSlot.Folder("folder-1", "Games", listOf("builtin.media/next")),
                loaded.slotAt(SlotPosition(0, 7)),
            )
        }

    @Test
    fun `damaged folders are repaired - blank ids, blank buttons, too many buttons, bad names`() {
        val actions = (0 until 60).joinToString(",") { "\"a$it\"" } + ",\"\""
        val text =
            """{"version":1,"profiles":[{"id":"p","name":"P","columns":4,"rows":2,"pages":[{"id":"g","name":"G","slots":[
                {"type":"folder","id":"f","name":"  ","actions":[$actions]},
                {"type":"folder","id":"","name":"No id"},
                {"type":"folder","id":"g2","name":"Games","actions":["x","","y"]}
            ]}]}]}"""
        val page =
            DataStoreProfileStore
                .read(text)
                .single()
                .pages
                .single()

        val first = page.slots[0] as DeckSlot.Folder
        assertEquals("Folder", first.name)
        assertEquals(36, first.actions.size)
        assertNull(page.slots[1], "a folder without an id reads as an empty cell")
        assertEquals(listOf("x", "y"), (page.slots[2] as DeckSlot.Folder).actions)
    }
}
