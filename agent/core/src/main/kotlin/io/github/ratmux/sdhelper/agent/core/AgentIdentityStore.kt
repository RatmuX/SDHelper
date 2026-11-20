package io.github.ratmux.sdhelper.agent.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Keeps the agent id stable across restarts, so phones recognize their computer. */
object AgentIdentityStore {
    fun loadOrCreate(
        directory: Path,
        name: String,
        version: String,
    ): AgentIdentity {
        val file = directory.resolve("agent.id")
        val id =
            if (Files.exists(file)) {
                Files.readString(file).trim()
            } else {
                UUID.randomUUID().toString().also {
                    Files.createDirectories(directory)
                    Files.writeString(file, it)
                }
            }
        return AgentIdentity(id, name, version)
    }
}
