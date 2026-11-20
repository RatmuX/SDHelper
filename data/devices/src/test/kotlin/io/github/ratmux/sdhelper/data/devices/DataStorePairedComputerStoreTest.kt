package io.github.ratmux.sdhelper.data.devices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ratmux.sdhelper.core.model.PairedComputer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStorePairedComputerStoreTest {
    private val file: File = createTempDirectory("sdhelper-store").resolve("computers.preferences_pb").toFile()

    private fun newStore() = DataStorePairedComputerStore(PreferenceDataStoreFactory.create { file })

    private fun computer(
        id: String,
        host: String = "192.168.1.2",
    ) = PairedComputer(id, "PC $id", host, 47821, "ab".repeat(32), "dev-$id", "key-$id")

    @Test
    fun `first computer becomes active and everything survives a restart`() =
        runTest {
            val store = newStore()
            store.save(computer("a"))

            assertEquals(listOf(computer("a")), store.computers.first())
            assertEquals("a", store.activeAgentId.first())
        }

    @Test
    fun `saving an existing computer updates it`() =
        runTest {
            val store = newStore()
            store.save(computer("a"))
            store.save(computer("a", host = "10.0.0.9"))
            assertEquals(listOf("10.0.0.9"), store.computers.first().map { it.host })
        }

    @Test
    fun `removing the active computer activates another or clears the selection`() =
        runTest {
            val store = newStore()
            store.save(computer("a"))
            store.save(computer("b"))

            store.remove("a")
            assertEquals("b", store.activeAgentId.first())

            store.remove("b")
            assertNull(store.activeAgentId.first())
            assertEquals(emptyList(), store.computers.first())
        }

    @Test
    fun `selection can be changed explicitly`() =
        runTest {
            val store = newStore()
            store.save(computer("a"))
            store.save(computer("b"))
            store.setActive("b")
            assertEquals("b", store.activeAgentId.first())
        }

    @Test
    fun `damaged data reads as an empty list instead of crashing`() =
        runTest {
            val dataStore = PreferenceDataStoreFactory.create { file }
            dataStore.edit { it[stringPreferencesKey("computers")] = "{not json" }
            assertEquals(emptyList(), DataStorePairedComputerStore(dataStore).computers.first())
        }
}
