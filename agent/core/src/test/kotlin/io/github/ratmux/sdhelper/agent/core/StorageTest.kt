package io.github.ratmux.sdhelper.agent.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StorageTest {
    private val dir: Path = Files.createTempDirectory("sdhelper-test")
    private val device = PairedDevice("d1", "Phone", "AAAA", 5)

    @Test
    fun `devices survive a restart`() {
        val file = dir.resolve("nested/devices.json")
        FileDeviceRegistry(file).add(device)

        val reloaded = FileDeviceRegistry(file)
        assertEquals(listOf(device), reloaded.devices.value)
        assertEquals(device, reloaded.find("d1"))
    }

    @Test
    fun `revoking removes the device from disk too`() {
        val file = dir.resolve("devices.json")
        val registry = FileDeviceRegistry(file)
        registry.add(device)

        assertTrue(registry.revoke("d1"))
        assertFalse(registry.revoke("d1"))
        assertTrue(FileDeviceRegistry(file).devices.value.isEmpty())
        assertFalse(dir.resolve("devices.json.tmp").exists())
    }

    @Test
    fun `adding the same device twice keeps one entry`() {
        val registry = FileDeviceRegistry(dir.resolve("devices.json"))
        registry.add(device)
        registry.add(device.copy(name = "Renamed"))
        assertEquals(listOf("Renamed"), registry.devices.value.map { it.name })
    }

    @Test
    fun `secrets are stable, random and private`() {
        val store = FileSecretStore(dir.resolve("secrets"))
        val first = store.getOrCreate("tls")

        assertEquals(first, store.getOrCreate("tls"))
        assertEquals(first, FileSecretStore(dir.resolve("secrets")).getOrCreate("tls"))
        assertNotEquals(first, store.getOrCreate("other"))
        assertTrue(first.length >= 32)

        val permissions = Files.getPosixFilePermissions(dir.resolve("secrets/tls.secret"))
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
    }

    @Test
    fun `agent id is created once and then kept`() {
        val first = AgentIdentityStore.loadOrCreate(dir, "PC", "1.0")
        val second = AgentIdentityStore.loadOrCreate(dir, "Renamed PC", "1.1")
        assertEquals(first.agentId, second.agentId)
        assertEquals("Renamed PC", second.agentName)
        assertTrue(first.agentId.isNotBlank())
    }

    @Test
    fun `secret names cannot escape the directory`() {
        assertFailsWith<IllegalArgumentException> { FileSecretStore(dir).getOrCreate("../evil") }
    }

    @Test
    fun `config directory follows platform conventions and the override`() {
        assertEquals("/h/.config/sdhelper", AgentPaths.configDirectory("Linux", emptyMap(), "/h").toString())
        assertEquals(
            "/x/sdhelper",
            AgentPaths.configDirectory("Linux", mapOf("XDG_CONFIG_HOME" to "/x"), "/h").toString(),
        )
        val windows = AgentPaths.configDirectory("Windows 11", mapOf("APPDATA" to "/appdata"), "/h")
        assertEquals("SDHelper", windows.fileName.toString())
        assertEquals("/o", AgentPaths.configDirectory("Linux", mapOf(AgentPaths.OVERRIDE_ENV to "/o"), "/h").toString())
    }
}
