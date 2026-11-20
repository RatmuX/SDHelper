package io.github.ratmux.sdhelper.agent.desktop

import io.github.ratmux.sdhelper.agent.core.AgentInfo
import io.github.ratmux.sdhelper.agent.core.AgentPaths
import io.github.ratmux.sdhelper.agent.core.platform.PlatformServices
import io.github.ratmux.sdhelper.agent.desktop.ui.ConsoleUi
import io.github.ratmux.sdhelper.agent.desktop.ui.SwingUi
import io.github.ratmux.sdhelper.agent.server.AgentServer
import kotlinx.coroutines.runBlocking
import java.awt.GraphicsEnvironment
import java.nio.file.Path

/**
 * Starts the agent.
 * - `--headless` (or a machine without a display) uses the terminal interface.
 * - `--port N` changes the listening port (default ${AgentServer.DEFAULT_PORT}).
 * - `--demo-platform` offers in-memory volume, microphone and media actions, to try the phone app without Windows.
 */
fun main(args: Array<String>) {
    val port = args.indexOf("--port").takeIf { it >= 0 }?.let { args.getOrNull(it + 1)?.toIntOrNull() }
    val headless = "--headless" in args || GraphicsEnvironment.isHeadless()
    val configDirectory = AgentPaths.configDirectory()
    val platform = createPlatform(demo = "--demo-platform" in args, configDirectory)
    println("${AgentInfo().banner()} starting")

    if (headless) {
        val ui = ConsoleUi()
        val runtime =
            AgentRuntime(approval = ui, platform = platform, configDirectory, port ?: AgentServer.DEFAULT_PORT)
        runtime.start()
        Runtime.getRuntime().addShutdownHook(Thread { runtime.stop() })
        runBlocking { ui.run(runtime) }
    } else {
        SwingUi.launch(port ?: AgentServer.DEFAULT_PORT, platform, configDirectory)
    }
}

private fun createPlatform(
    demo: Boolean,
    configDirectory: Path,
): PlatformServices =
    when {
        demo -> {
            println("DEMO PLATFORM: volume, microphone and media are simulated in memory.")
            PlatformServices.demo(configDirectory)
        }

        else -> {
            PlatformServices.fileBased(configDirectory)
        }
    }
