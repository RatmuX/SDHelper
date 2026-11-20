package io.github.ratmux.sdhelper.agent.platform.windows

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeHelperLocatorTest {
    private val dir = Files.createTempDirectory("sdhelper-locator")

    @Test
    fun `candidates are tried in a fixed order`() {
        val list =
            NativeHelperLocator.candidates(
                env = mapOf(NativeHelperLocator.ENV to "C:/custom/helper.exe"),
                launcherPath = "C:/Apps/SDHelperAgent/SDHelperAgent.exe",
                workingDirectory = Paths.get("/work"),
            )
        assertEquals(
            listOf(
                Paths.get("C:/custom/helper.exe"),
                Paths.get("C:/Apps/SDHelperAgent/sdhelper-native.exe"),
                Paths.get("/work/sdhelper-native.exe"),
                Paths.get("/work/native/windows/target/release/sdhelper-native.exe"),
            ),
            list,
        )
    }

    @Test
    fun `missing configuration yields fewer candidates`() {
        assertEquals(2, NativeHelperLocator.candidates(emptyMap(), null, Paths.get("/work")).size)
        assertEquals(
            2,
            NativeHelperLocator.candidates(mapOf(NativeHelperLocator.ENV to " "), null, Paths.get("/work")).size,
        )
    }

    @Test
    fun `the first candidate that is a file wins`() {
        val second = Files.createFile(dir.resolve("second.exe"))
        val third = Files.createFile(dir.resolve("third.exe"))
        assertEquals(second, NativeHelperLocator.find(listOf(dir.resolve("missing.exe"), second, third)))
        assertNull(NativeHelperLocator.find(listOf(dir.resolve("missing.exe"), dir)))
    }
}
