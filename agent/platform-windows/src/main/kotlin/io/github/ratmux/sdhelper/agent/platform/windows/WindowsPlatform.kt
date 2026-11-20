package io.github.ratmux.sdhelper.agent.platform.windows

import io.github.ratmux.sdhelper.agent.core.platform.PlatformServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Path

/** The Windows platform: audio, media and DPAPI secrets, all through the native helper. */
object WindowsPlatform {
    fun create(
        helperExecutable: Path,
        configDirectory: Path,
    ): PlatformServices = create(ExecutableLauncher(helperExecutable), configDirectory)

    fun create(
        launcher: ProcessLauncher,
        configDirectory: Path,
    ): PlatformServices {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val helper = NativeHelper(launcher, scope)
        return PlatformServices(
            secrets = DpapiSecretStore(helper, configDirectory.resolve("secrets")),
            audio = WindowsAudioController(helper, scope),
            media = WindowsMediaController(helper, scope),
            close = {
                helper.close()
                scope.cancel()
            },
        )
    }
}
