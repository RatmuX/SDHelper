package io.github.ratmux.sdhelper.agent.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64

/** Stores small secrets of the agent, such as the password of its TLS key store. */
interface SecretStore {
    /** Returns the stored secret, creating and storing a random one on first use. */
    fun getOrCreate(name: String): String
}

/**
 * Development and fallback implementation: one file per secret in the agent's config directory.
 * On POSIX systems the file is readable by the owner only; on Windows it relies on the default ACL of the
 * user profile. Windows DPAPI protection is added in Phase 3b (docs/SECURITY.md).
 */
class FileSecretStore(
    private val directory: Path,
    private val random: SecureRandom = SecureRandom(),
) : SecretStore {
    @Synchronized
    override fun getOrCreate(name: String): String {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid secret name" }
        val file = directory.resolve("$name.secret")
        if (Files.exists(file)) return Files.readString(file).trim()

        Files.createDirectories(directory)
        val secret =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteArray(SECRET_BYTES).also(random::nextBytes),
            )
        createPrivateFile(file)
        Files.writeString(file, secret)
        return secret
    }

    /** Creates the file owner-only from the start, so the secret is never readable by others, even briefly. */
    private fun createPrivateFile(file: Path) {
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } catch (_: UnsupportedOperationException) {
            Files.createFile(file) // not a POSIX file system (Windows): default user-profile ACL
        }
    }

    private companion object {
        const val SECRET_BYTES = 32
    }
}
