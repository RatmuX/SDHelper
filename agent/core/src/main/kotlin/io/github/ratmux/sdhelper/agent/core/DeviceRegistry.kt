package io.github.ratmux.sdhelper.agent.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** A phone that completed pairing. [publicKey] is the EC P-256 key in X.509 form, standard Base64. */
@Serializable
data class PairedDevice(
    val deviceId: String,
    val name: String,
    val publicKey: String,
    val pairedAtMillis: Long,
)

/** Persistent list of paired devices; revoking a device removes its ability to authenticate. */
interface DeviceRegistry {
    val devices: StateFlow<List<PairedDevice>>

    fun find(deviceId: String): PairedDevice?

    fun add(device: PairedDevice)

    fun revoke(deviceId: String): Boolean
}

/** [DeviceRegistry] stored as a JSON file, written atomically. */
class FileDeviceRegistry(
    private val file: Path,
) : DeviceRegistry {
    @Serializable
    private data class Stored(
        val devices: List<PairedDevice> = emptyList(),
    )

    private val json = Json { prettyPrint = true }
    private val state = MutableStateFlow(load())

    override val devices: StateFlow<List<PairedDevice>> get() = state

    override fun find(deviceId: String): PairedDevice? = state.value.firstOrNull { it.deviceId == deviceId }

    @Synchronized
    override fun add(device: PairedDevice) {
        update { current -> current.filterNot { it.deviceId == device.deviceId } + device }
    }

    @Synchronized
    override fun revoke(deviceId: String): Boolean {
        if (find(deviceId) == null) return false
        update { current -> current.filterNot { it.deviceId == deviceId } }
        return true
    }

    private fun update(transform: (List<PairedDevice>) -> List<PairedDevice>) {
        val next = transform(state.value)
        save(next)
        state.value = next
    }

    private fun load(): List<PairedDevice> {
        if (!Files.exists(file)) return emptyList()
        return json.decodeFromString(Stored.serializer(), Files.readString(file)).devices
    }

    private fun save(devices: List<PairedDevice>) {
        file.parent?.let { Files.createDirectories(it) }
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, json.encodeToString(Stored.serializer(), Stored(devices)))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
