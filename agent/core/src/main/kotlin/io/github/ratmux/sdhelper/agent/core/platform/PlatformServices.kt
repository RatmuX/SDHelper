package io.github.ratmux.sdhelper.agent.core.platform

import io.github.ratmux.sdhelper.agent.core.FileSecretStore
import io.github.ratmux.sdhelper.agent.core.SecretStore
import java.nio.file.Path

/**
 * Everything the agent needs from the operating system (docs/ARCHITECTURE.md, "Agent port interfaces").
 * A `null` controller means the platform does not offer it; its actions are then not published.
 * Windows implements this in `agent:platform-windows`; other systems start from [fileBased].
 */
class PlatformServices(
    val secrets: SecretStore,
    val audio: AudioController? = null,
    val media: MediaController? = null,
    /** Called once when the agent stops, to end helper processes and the like. */
    val close: () -> Unit = {},
) {
    companion object {
        /** No system integration beyond a file-based secret store: the agent runs and pairs, but offers no actions. */
        fun fileBased(configDirectory: Path) = PlatformServices(FileSecretStore(configDirectory.resolve("secrets")))

        /** Demo platform with in-memory audio and media, for trying the phone app without Windows. */
        fun demo(configDirectory: Path) =
            PlatformServices(
                secrets = FileSecretStore(configDirectory.resolve("secrets")),
                audio = InMemoryAudioController(),
                media = InMemoryMediaController(),
            )
    }
}
