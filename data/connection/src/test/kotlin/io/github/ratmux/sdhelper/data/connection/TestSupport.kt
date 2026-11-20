package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.agent.core.AgentIdentity
import io.github.ratmux.sdhelper.agent.core.ChallengeAuthenticator
import io.github.ratmux.sdhelper.agent.core.ChannelRegistry
import io.github.ratmux.sdhelper.agent.core.ControlSession
import io.github.ratmux.sdhelper.agent.core.DeckChannelHandler
import io.github.ratmux.sdhelper.agent.core.FileDeviceRegistry
import io.github.ratmux.sdhelper.agent.core.FileSecretStore
import io.github.ratmux.sdhelper.agent.core.PairingApproval
import io.github.ratmux.sdhelper.agent.core.PairingApprovalRequest
import io.github.ratmux.sdhelper.agent.core.PairingService
import io.github.ratmux.sdhelper.agent.core.ProviderRegistry
import io.github.ratmux.sdhelper.agent.core.platform.InMemoryAudioController
import io.github.ratmux.sdhelper.agent.core.platform.InMemoryMediaController
import io.github.ratmux.sdhelper.agent.server.AgentServer
import io.github.ratmux.sdhelper.agent.server.TlsIdentity
import io.github.ratmux.sdhelper.core.model.ActionCatalogCache
import io.github.ratmux.sdhelper.core.model.ComputerDiscovery
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DiscoveredComputer
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import io.github.ratmux.sdhelper.plugins.builtinmedia.MediaProvider
import io.github.ratmux.sdhelper.plugins.builtinsystemaudio.SystemAudioProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** A real agent on a loopback port: TLS key, device list on disk, pairing and the protocol state machine. */
class AgentFixture(
    port: Int = 0,
) {
    private val dir = Files.createTempDirectory("sdhelper-phone-test")
    val tls = TlsIdentity.loadOrCreate(dir.resolve("tls.p12"), FileSecretStore(dir.resolve("secrets")))
    val registry = FileDeviceRegistry(dir.resolve("devices.json"))
    val approvals = mutableListOf<PairingApprovalRequest>()
    var approve = true
    val identity = AgentIdentity("agent-it", "Desk PC", "test")

    private val pairing =
        PairingService(
            registry,
            tls.fingerprint,
            PairingApproval {
                approvals += it
                approve
            },
        )
    private val authenticator = ChallengeAuthenticator(registry, tls.fingerprint)

    /** The simulated computer: volume, microphone and media, served on the deck channel. */
    val audio = InMemoryAudioController(initialVolume = 50)
    val media = InMemoryMediaController()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channels =
        ChannelRegistry().also {
            it.register(
                DeckChannelHandler(ProviderRegistry(listOf(SystemAudioProvider(audio), MediaProvider(media))), scope),
            )
        }
    private val server =
        AgentServer(
            tls,
            { sink -> ControlSession(identity, pairing, authenticator, channels, sink = sink) },
            port = port,
            bindHost = "127.0.0.1",
        )
    val port: Int = server.start()

    fun newOffer() = pairing.newOffer()

    fun stop() {
        server.stop()
        scope.cancel()
    }
}

/** Plays the Android Keystore: EC P-256 keys in memory. */
class JvmDeviceKeyStore : DeviceKeyStore {
    private val keys = mutableMapOf<String, KeyPair>()

    val aliases: Set<String> get() = keys.keys

    override fun generate(alias: String): String {
        val pair =
            KeyPairGenerator
                .getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
        keys[alias] = pair
        return Base64.getEncoder().encodeToString(pair.public.encoded)
    }

    override fun sign(
        alias: String,
        data: ByteArray,
    ): ByteArray =
        Signature
            .getInstance("SHA256withECDSA")
            .apply {
                initSign(keys.getValue(alias).private)
                update(data)
            }.sign()

    override fun delete(alias: String) {
        keys.remove(alias)
    }
}

class FakeStore : PairedComputerStore {
    private val list = MutableStateFlow<List<PairedComputer>>(emptyList())
    private val active = MutableStateFlow<String?>(null)

    override val computers: Flow<List<PairedComputer>> get() = list
    override val activeAgentId: Flow<String?> get() = active

    override suspend fun save(computer: PairedComputer) {
        list.value = list.value.filterNot { it.agentId == computer.agentId } + computer
        if (active.value == null) active.value = computer.agentId
    }

    override suspend fun remove(agentId: String) {
        list.value = list.value.filterNot { it.agentId == agentId }
    }

    override suspend fun setActive(agentId: String?) {
        active.value = agentId
    }

    val current: List<PairedComputer> get() = list.value
}

class FakeLocalNetwork(
    initial: Boolean = true,
) : LocalNetworkAccess {
    private val flow = MutableStateFlow(initial)
    override val granted: StateFlow<Boolean> get() = flow

    fun set(value: Boolean) {
        flow.value = value
    }

    override fun refresh() = Unit
}

class FakeDiscovery : ComputerDiscovery {
    val announcements = MutableSharedFlow<List<DiscoveredComputer>>(replay = 1)

    override fun discover(): Flow<List<DiscoveredComputer>> = announcements.map { it }
}

class FakeCatalogCache : ActionCatalogCache {
    private val state = MutableStateFlow<List<DeckAction>>(emptyList())
    val remembered: List<DeckAction> get() = state.value

    override val actions: Flow<List<DeckAction>> get() = state

    override suspend fun update(actions: List<DeckAction>) {
        state.value = (state.value.associateBy { it.id } + actions.associateBy { it.id }).values.toList()
    }
}
