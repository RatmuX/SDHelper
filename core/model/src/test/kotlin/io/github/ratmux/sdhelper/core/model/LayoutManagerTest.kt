package io.github.ratmux.sdhelper.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LayoutManagerTest {
    private class FakeProfiles(
        initial: List<DeckProfile>,
    ) : ProfileStore {
        val list = MutableStateFlow(initial)
        val active = MutableStateFlow<String?>(initial.firstOrNull()?.id)
        override val profiles: Flow<List<DeckProfile>> get() = list
        override val activeProfileId: Flow<String?> get() = active

        override suspend fun save(profile: DeckProfile) {
            list.value =
                if (list.value.any { it.id == profile.id }) {
                    list.value.map { if (it.id == profile.id) profile else it }
                } else {
                    list.value + profile
                }
        }

        override suspend fun delete(profileId: String): Boolean {
            if (list.value.size <= 1) return false
            list.value = list.value.filterNot { it.id == profileId }
            if (active.value == profileId) active.value = list.value.first().id
            return true
        }

        override suspend fun setActive(profileId: String) {
            active.value = profileId
        }
    }

    private var counter = 0
    private val newId = { "id-${counter++}" }
    private val base = LayoutPresets.EVERYDAY.create("en", newId = { "base-${counter++}" })
    private val store = FakeProfiles(listOf(base))
    private val manager = LayoutManager(store, newId)

    @Test
    fun `an empty layout gets the chosen name or the fallback, one empty page, and becomes active`() =
        runTest {
            manager.createEmpty("  Mine ", fallbackName = "New", firstPageName = "Page 1")
            manager.createEmpty("   ", fallbackName = "New", firstPageName = "Page 1")

            assertEquals(listOf("Default", "Mine", "New"), store.list.value.map { it.name })
            val created = store.list.value.last()
            assertEquals(1, created.pages.size)
            assertEquals(0, created.buttonCount)
            assertEquals(created.id, store.active.value)
        }

    @Test
    fun `names stay unique and within the length limit`() =
        runTest {
            val long = "n".repeat(LayoutLimits.MAX_NAME_LENGTH)
            val store = FakeProfiles(listOf(base.copy(name = long)))
            LayoutManager(store, newId).saveCopy(base.id, "")

            val copy = store.list.value.last()
            assertTrue(copy.name.length <= LayoutLimits.MAX_NAME_LENGTH)
            assertTrue(copy.name.endsWith(" (2)"))
        }

    @Test
    fun `a copy has new ids but the same buttons, and the active layout stays`() =
        runTest {
            manager.saveCopy(base.id, "Evening")

            val copy = store.list.value.last()
            assertEquals("Evening", copy.name)
            assertNotEquals(base.id, copy.id)
            assertEquals(base.pages.map { it.slots }, copy.pages.map { it.slots })
            assertEquals(base.pages.size * 2, (base.pages + copy.pages).map { it.id }.toSet().size)
            assertEquals(base.id, store.active.value)
        }

    @Test
    fun `unknown presets and layouts are ignored, and the last layout cannot be deleted`() =
        runTest {
            manager.addFromPreset("nonexistent", "x", "en")
            manager.saveCopy("missing", "x")
            manager.rename("missing", "x")
            assertEquals(1, store.list.value.size)

            assertFalse(manager.delete(base.id))
            manager.addFromPreset("gaming", "", "tr")
            assertEquals(
                "Oyun",
                store.list.value
                    .last()
                    .name,
            )
            assertTrue(manager.delete(base.id))
            assertEquals(
                store.list.value
                    .single()
                    .id,
                store.active.value,
            )
        }
}
