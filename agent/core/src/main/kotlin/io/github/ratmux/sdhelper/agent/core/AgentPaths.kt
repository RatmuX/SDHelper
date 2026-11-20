package io.github.ratmux.sdhelper.agent.core

import java.nio.file.Path
import java.nio.file.Paths

/** Where the agent keeps its state (device list, TLS key store, secrets). */
object AgentPaths {
    /** Environment variable that overrides the default location, useful for tests and portable setups. */
    const val OVERRIDE_ENV = "SDHELPER_CONFIG_DIR"

    fun configDirectory(
        osName: String = System.getProperty("os.name").orEmpty(),
        env: Map<String, String> = System.getenv(),
        home: String = System.getProperty("user.home").orEmpty(),
    ): Path {
        env[OVERRIDE_ENV]?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        return if (osName.startsWith("Windows", ignoreCase = true)) {
            val base = env["APPDATA"]?.takeIf { it.isNotBlank() } ?: Paths.get(home, "AppData", "Roaming").toString()
            Paths.get(base, "SDHelper")
        } else {
            val base = env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() } ?: Paths.get(home, ".config").toString()
            Paths.get(base, "sdhelper")
        }
    }
}
