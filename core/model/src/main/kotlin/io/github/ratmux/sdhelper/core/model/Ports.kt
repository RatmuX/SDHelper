package io.github.ratmux.sdhelper.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Persistent list of paired computers and which one is in use. Implemented by `data:devices`. */
interface PairedComputerStore {
    val computers: Flow<List<PairedComputer>>

    /** Agent id of the computer to connect to, or null. */
    val activeAgentId: Flow<String?>

    suspend fun save(computer: PairedComputer)

    suspend fun remove(agentId: String)

    suspend fun setActive(agentId: String?)
}

/** Read-only view of the current connection, for screens. Implemented by `data:connection`. */
interface ConnectionStatus {
    val state: StateFlow<ConnectionState>
}

/** Computers announced on the local network, updated live. Best effort; may stay empty. */
interface ComputerDiscovery {
    fun discover(): Flow<List<DiscoveredComputer>>
}

enum class PairingFailure {
    /** Could not reach the computer. */
    UNREACHABLE,

    /** QR flow: the computer presented another certificate than the one in the QR code. */
    CERTIFICATE_MISMATCH,

    /** Wrong or expired token/PIN, or the pairing window was closed on the PC. */
    REJECTED,

    /** The person at the PC declined. */
    DENIED,

    /** Too many wrong attempts; a new code must be shown on the PC. */
    LOCKED,

    PROTOCOL,
}

sealed interface PairingOutcome {
    data class Success(
        val computer: PairedComputer,
    ) : PairingOutcome

    data class Failure(
        val reason: PairingFailure,
    ) : PairingOutcome
}

/** Pairs this phone with a computer. Implemented by `data:connection`. */
interface PairingClient {
    /** QR flow: [fingerprint] and [token] come from the scanned code. */
    suspend fun pairWithQr(
        host: String,
        port: Int,
        fingerprint: String,
        token: String,
    ): PairingOutcome

    /** Removes the key this phone registered for [computer]; call when the computer is forgotten. */
    fun forget(computer: PairedComputer)

    /**
     * PIN flow. [onVerificationCode] receives the short code to compare with the one shown on the PC
     * while the person there decides.
     */
    suspend fun pairWithPin(
        host: String,
        port: Int,
        pin: String,
        onVerificationCode: (String) -> Unit,
    ): PairingOutcome
}

/**
 * Whether the app may talk to devices on the local network. Since Android 17 (target SDK 37) this is a runtime
 * permission (`ACCESS_LOCAL_NETWORK`); without it connections to the PC silently time out, so nothing tries to
 * connect until it is granted. Implemented by the app module.
 */
interface LocalNetworkAccess {
    val granted: StateFlow<Boolean>

    /** Re-reads the permission, after a request was answered or the person came back from system settings. */
    fun refresh()

    companion object {
        const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

        /** First Android API level that enforces the permission for apps targeting it. */
        const val MIN_SDK = 37
    }
}

/** Light or dark appearance. [SYSTEM] follows the phone until the person picks one. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** Whether the dark palette applies, given whether the phone itself is in dark mode. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
        }
}

/** The modes of the app, shown in the navigation drawer. Only the Stream Deck exists so far. */
enum class AppMode { STREAM_DECK, TOUCH_PAD, KEYBOARD }

/** Phone-wide preferences. */
data class AppSettings(
    /** Short vibration when a button is pressed on the control panel. */
    val hapticFeedback: Boolean = true,
    /** Keep the screen on while the control panel is open. */
    val keepScreenOn: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The mode the home screen opens in, restored on the next start. */
    val lastMode: AppMode = AppMode.STREAM_DECK,
)

/** Persistent [AppSettings]. Implemented by `data:settings`. */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Persistent layouts. Implemented by `data:profiles`. There is always at least one profile once the app has started. */
interface ProfileStore {
    val profiles: Flow<List<DeckProfile>>

    /** Id of the profile shown on the control panel, or null before the first profile exists. */
    val activeProfileId: Flow<String?>

    /** Adds the profile or replaces the one with the same id. The first profile ever saved becomes active. */
    suspend fun save(profile: DeckProfile)

    /** Returns false (and deletes nothing) for the last remaining profile. Deleting the active one activates another. */
    suspend fun delete(profileId: String): Boolean

    suspend fun setActive(profileId: String)
}

/** The last known actions of any computer, so layouts show and can be edited while no computer is connected. */
interface ActionCatalogCache {
    val actions: Flow<List<DeckAction>>

    /** Adds new actions and refreshes known ones; actions of other computers stay. */
    suspend fun update(actions: List<DeckAction>)
}
