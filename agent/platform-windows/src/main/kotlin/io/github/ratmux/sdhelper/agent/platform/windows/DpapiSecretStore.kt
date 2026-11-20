package io.github.ratmux.sdhelper.agent.platform.windows

import io.github.ratmux.sdhelper.agent.core.SecretStore
import io.github.ratmux.sdhelper.agent.core.platform.PlatformException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64

/**
 * Secrets encrypted with the Windows Data Protection API through the native helper: the files hold ciphertext
 * that only the same Windows user can decrypt. A plain `.secret` file from the first versions is encrypted in
 * place and removed. There is no silent fallback to plain files: if the helper is missing this fails loudly.
 */
class DpapiSecretStore(
    private val helper: HelperRpc,
    private val directory: Path,
    private val random: SecureRandom = SecureRandom(),
) : SecretStore {
    @Synchronized
    override fun getOrCreate(name: String): String {
        require(name.matches(NAME)) { "Invalid secret name" }
        val protectedFile = directory.resolve("$name.secret.dpapi")
        val legacyFile = directory.resolve("$name.secret")

        if (Files.exists(
                protectedFile,
            )
        ) {
            return unprotect(Base64.getDecoder().decode(Files.readString(protectedFile).trim()))
        }

        Files.createDirectories(directory)
        val secret =
            if (Files.exists(legacyFile)) {
                Files.readString(legacyFile).trim()
            } else {
                Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(SECRET_BYTES).also(random::nextBytes))
            }
        Files.writeString(
            protectedFile,
            Base64.getEncoder().encodeToString(protect(secret.toByteArray(Charsets.UTF_8))),
        )
        if (Files.exists(legacyFile)) overwriteAndDelete(legacyFile)
        return secret
    }

    private fun protect(data: ByteArray): ByteArray = transform("dpapi.protect", data)

    private fun unprotect(data: ByteArray): String = String(transform("dpapi.unprotect", data), Charsets.UTF_8)

    private fun transform(
        method: String,
        data: ByteArray,
    ): ByteArray =
        try {
            val answer =
                runBlocking {
                    helper.call(
                        method,
                        buildJsonObject { put("data", Base64.getEncoder().encodeToString(data)) },
                    )
                }
            Base64.getDecoder().decode(
                answer.jsonObject
                    .getValue("data")
                    .jsonPrimitive.content,
            )
        } catch (e: RpcException) {
            throw PlatformException("Windows could not protect or unprotect a secret (${e.message})", e)
        } catch (e: HelperClosedException) {
            throw PlatformException("The Windows helper is required to protect secrets but is not running", e)
        } catch (e: HelperTimeoutException) {
            throw PlatformException("The Windows helper did not answer while protecting a secret", e)
        }

    /** Best effort: overwrite the plain text first, so it is not left behind in a recoverable state. */
    private fun overwriteAndDelete(file: Path) {
        runCatching { Files.write(file, ByteArray(Files.size(file).toInt())) }
        Files.delete(file)
    }

    private companion object {
        val NAME = Regex("[A-Za-z0-9._-]+")
        const val SECRET_BYTES = 32
    }
}
