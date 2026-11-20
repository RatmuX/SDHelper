package io.github.ratmux.sdhelper.core.model

/** Why a connection stopped and is not retried automatically. */
enum class FailureReason {
    /** The computer presents another certificate than the one paired. Never retried: could be an impostor. */
    CERTIFICATE_MISMATCH,

    /** The computer no longer knows this phone (it was removed on the PC). */
    REVOKED,

    /** Unexpected protocol behaviour, for example an unsupported version. */
    PROTOCOL,
}

sealed interface ConnectionState {
    /** No computer selected, or the app is in the background. */
    data object Disconnected : ConnectionState

    /** Trying to reach the computer; [attempt] counts from 1 and grows while it stays unreachable. */
    data class Connecting(
        val attempt: Int,
    ) : ConnectionState

    data object Authenticating : ConnectionState

    data class Connected(
        val computerName: String,
    ) : ConnectionState

    data class Failed(
        val reason: FailureReason,
    ) : ConnectionState
}
