package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.AuthResponse
import io.github.ratmux.sdhelper.core.protocol.AuthSigning
import io.github.ratmux.sdhelper.core.protocol.PairRequest
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

const val TEST_FINGERPRINT = "ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12"

/** A clock the tests can move forward. */
class MutableClock(
    private var millis: Long = 1_000_000L,
) : Clock() {
    fun advance(byMillis: Long) {
        millis += byMillis
    }

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(millis)
}

/** Plays the phone's role: holds an EC P-256 key like the Android Keystore key and signs challenges. */
class FakePhone(
    val name: String = "Test Phone",
    val keyPair: KeyPair = newKeyPair(),
) {
    val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    fun pairRequestWithToken(token: String) = PairRequest(name, publicKeyBase64, token = token)

    fun pairRequestWithPin(pin: String) = PairRequest(name, publicKeyBase64, pin = pin)

    fun authResponse(
        deviceId: String,
        nonce: ByteArray,
        certificateFingerprint: String = TEST_FINGERPRINT,
    ): AuthResponse {
        val signature = Signature.getInstance(AuthSigning.ALGORITHM)
        signature.initSign(keyPair.private)
        signature.update(AuthSigning.signedBytes(nonce, certificateFingerprint))
        return AuthResponse(deviceId, Base64.getEncoder().encodeToString(signature.sign()))
    }

    companion object {
        fun newKeyPair(): KeyPair =
            KeyPairGenerator
                .getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
    }
}

/** In-memory registry for tests that do not care about persistence. */
class InMemoryDeviceRegistry : DeviceRegistry {
    private val state = kotlinx.coroutines.flow.MutableStateFlow<List<PairedDevice>>(emptyList())
    override val devices get() = state

    override fun find(deviceId: String) = state.value.firstOrNull { it.deviceId == deviceId }

    override fun add(device: PairedDevice) {
        state.value = state.value + device
    }

    override fun revoke(deviceId: String): Boolean {
        val existed = find(deviceId) != null
        state.value = state.value.filterNot { it.deviceId == deviceId }
        return existed
    }
}
