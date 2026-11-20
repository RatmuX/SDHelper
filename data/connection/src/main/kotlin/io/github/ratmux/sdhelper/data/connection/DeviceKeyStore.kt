package io.github.ratmux.sdhelper.data.connection

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Keys that identify this phone to one computer. The private key never leaves the secure store. */
interface DeviceKeyStore {
    /** Creates an EC P-256 key under [alias] and returns its public key (X.509, standard Base64). */
    fun generate(alias: String): String

    /** DER-encoded `SHA256withECDSA` signature of [data]. */
    fun sign(
        alias: String,
        data: ByteArray,
    ): ByteArray

    fun delete(alias: String)
}

/** [DeviceKeyStore] backed by the Android Keystore (hardware-backed where the device has it). */
class AndroidKeystoreDeviceKeyStore : DeviceKeyStore {
    private val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun generate(alias: String): String {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        KeyPairGenerator
            .getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                PROVIDER,
            ).apply { initialize(spec) }
            .generateKeyPair()
        val publicKey = keyStore.getCertificate(alias).publicKey
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    override fun sign(
        alias: String,
        data: ByteArray,
    ): ByteArray {
        val key = keyStore.getKey(alias, null) as PrivateKey
        return Signature
            .getInstance("SHA256withECDSA")
            .apply {
                initSign(key)
                update(data)
            }.sign()
    }

    override fun delete(alias: String) {
        runCatching { keyStore.deleteEntry(alias) }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
    }
}
