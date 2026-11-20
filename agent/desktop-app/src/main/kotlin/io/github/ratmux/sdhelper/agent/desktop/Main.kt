package io.github.ratmux.sdhelper.agent.desktop

import io.github.ratmux.sdhelper.agent.core.AgentInfo
import io.github.ratmux.sdhelper.agent.desktop.ui.ConsoleUi
import io.github.ratmux.sdhelper.agent.desktop.ui.SwingUi
import io.github.ratmux.sdhelper.agent.server.AgentServer
import kotlinx.coroutines.runBlocking
import java.awt.GraphicsEnvironment

/**
 * Starts the agent. `--headless` (or a machine without a display) uses the terminal interface;
 * `--port N` changes the listening port (default ${AgentServer.DEFAULT_PORT}).
 */
fun main(args: Array<String>) {
    val port = args.indexOf("--port").takeIf { it >= 0 }?.let { args.getOrNull(it + 1)?.toIntOrNull() }
    val headless = "--headless" in args || GraphicsEnvironment.isHeadless()
    println("${AgentInfo().banner()} starting")

    if (headless) {
        val ui = ConsoleUi()
        val runtime = AgentRuntime(approval = ui, port = port ?: AgentServer.DEFAULT_PORT)
        runtime.start()
        Runtime.getRuntime().addShutdownHook(Thread { runtime.stop() })
        runBlocking { ui.run(runtime) }
    } else {
        SwingUi.launch(port ?: AgentServer.DEFAULT_PORT)
    }
}
