package io.github.ratmux.sdhelper

import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutBootstrapTest {
    private class FakeProfiles(
        initial: List<DeckProfile> = emptyList(),
    ) : ProfileStore {
        val list = MutableStateFlow(initial)
        val active = MutableStateFlow<String?>(initial.firstOrNull()?.id)
        override val profiles: Flow<List<DeckProfile>> get() = list
        override val activeProfileId: Flow<String?> get() = active

        override suspend fun save(profile: DeckProfile) {
            list.value = list.value + profile
            if (active.value == null) active.value = profile.id
        }

        override suspend fun delete(profileId: String) = false

        override suspend fun setActive(profileId: String) {
            active.value = profileId
        }
    }

    @Test
    fun `the first start creates the four starter layouts and the default one is active`() {
        val store = FakeProfiles()
        LayoutBootstrap(store, CoroutineScope(Dispatchers.Unconfined), language = "tr")

        assertEquals(listOf("Varsayılan", "Yayın modu", "Toplantı", "Oyun"), store.list.value.map { it.name })
        assertEquals(
            store.list.value
                .first()
                .id,
            store.active.value,
        )
    }

    @Test
    fun `english phones get english names`() {
        val store = FakeProfiles()
        LayoutBootstrap(store, CoroutineScope(Dispatchers.Unconfined), language = "en")
        assertEquals(listOf("Default", "Streaming", "Meeting", "Gaming"), store.list.value.map { it.name })
    }

    @Test
    fun `existing layouts are left alone`() {
        val existing =
            io.github.ratmux.sdhelper.core.model.LayoutPresets.GAMING
                .create("en")
        val store = FakeProfiles(listOf(existing))
        LayoutBootstrap(store, CoroutineScope(Dispatchers.Unconfined), language = "en")
        assertEquals(listOf(existing), store.list.value)
    }
}
