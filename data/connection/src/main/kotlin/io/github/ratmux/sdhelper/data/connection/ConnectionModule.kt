package io.github.ratmux.sdhelper.data.connection

import android.os.Build
import io.github.ratmux.sdhelper.core.model.ComputerDiscovery
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.PairingClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier of the application-wide `CoroutineScope` that the app module provides. */
val APP_SCOPE = named("appScope")

/**
 * Koin module of the phone side of the connection.
 * Needs a `PairedComputerStore`, a `LocalNetworkAccess` and the [APP_SCOPE] coroutine scope.
 */
val connectionModule =
    module {
        single<DeviceKeyStore> { AndroidKeystoreDeviceKeyStore() }
        single { DefaultConnectionManager(keys = get(), deviceName = { Build.MODEL }, scope = get(APP_SCOPE)) }
        single<ConnectionStatus> { get<DefaultConnectionManager>() }
        single<DeckTransport> { get<DefaultConnectionManager>() }
        single<DeckActions> { DefaultDeckActions(get(), get(), get(APP_SCOPE)) }
        single<PairingClient> { DefaultPairingClient(get()) { Build.MODEL } }
        single<ComputerDiscovery> { NsdComputerDiscovery(androidContext()) }
        single(createdAtStart = true) {
            ConnectionSupervisor(get(APP_SCOPE), get(), get(), get(), get())
        }
    }
