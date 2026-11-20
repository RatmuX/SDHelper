package io.github.ratmux.sdhelper.data.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Paired computers as a JSON list inside Preferences DataStore. Secrets are not stored here: keys stay in the Keystore. */
class DataStorePairedComputerStore(
    private val dataStore: DataStore<Preferences>,
) : PairedComputerStore {
    @Serializable
    private data class Stored(
        val agentId: String,
        val name: String,
        val host: String,
        val port: Int,
        val fingerprint: String,
        val deviceId: String,
        val keyAlias: String,
    )

    override val computers: Flow<List<PairedComputer>> = dataStore.data.map { decode(it[COMPUTERS]) }

    override val activeAgentId: Flow<String?> = dataStore.data.map { it[ACTIVE] }

    override suspend fun save(computer: PairedComputer) {
        dataStore.edit { prefs ->
            val others = decode(prefs[COMPUTERS]).filterNot { it.agentId == computer.agentId }
            prefs[COMPUTERS] = encode(others + computer)
            if (prefs[ACTIVE] == null) prefs[ACTIVE] = computer.agentId
        }
    }

    override suspend fun remove(agentId: String) {
        dataStore.edit { prefs ->
            val remaining = decode(prefs[COMPUTERS]).filterNot { it.agentId == agentId }
            prefs[COMPUTERS] = encode(remaining)
            if (prefs[ACTIVE] == agentId) {
                remaining.firstOrNull()?.let { prefs[ACTIVE] = it.agentId } ?: prefs.remove(ACTIVE)
            }
        }
    }

    override suspend fun setActive(agentId: String?) {
        dataStore.edit { prefs ->
            if (agentId == null) prefs.remove(ACTIVE) else prefs[ACTIVE] = agentId
        }
    }

    private fun encode(list: List<PairedComputer>): String =
        json.encodeToString(
            LIST,
            list.map { Stored(it.agentId, it.name, it.host, it.port, it.fingerprint, it.deviceId, it.keyAlias) },
        )

    /** A damaged value must never crash the app: it reads as an empty list. */
    private fun decode(text: String?): List<PairedComputer> =
        text
            ?.let { runCatching { json.decodeFromString(LIST, it) }.getOrNull() }
            .orEmpty()
            .map { PairedComputer(it.agentId, it.name, it.host, it.port, it.fingerprint, it.deviceId, it.keyAlias) }

    companion object {
        private val COMPUTERS = stringPreferencesKey("computers")
        private val ACTIVE = stringPreferencesKey("active_agent")
        private val json = Json { ignoreUnknownKeys = true }
        private val LIST = ListSerializer(Stored.serializer())

        fun create(context: Context): PairedComputerStore =
            DataStorePairedComputerStore(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("paired_computers") },
            )
    }
}
