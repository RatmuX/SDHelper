package io.github.ratmux.sdhelper

import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import io.github.ratmux.sdhelper.data.connection.APP_SCOPE
import io.github.ratmux.sdhelper.data.connection.connectionModule
import io.github.ratmux.sdhelper.data.devices.DataStorePairedComputerStore
import io.github.ratmux.sdhelper.feature.connection.connectionFeatureModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Things only the app can provide: the application scope, storage and the permission state. */
val appModule =
    module {
        single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single<PairedComputerStore> { DataStorePairedComputerStore.create(androidContext()) }
        single<LocalNetworkAccess> { AndroidLocalNetworkAccess(androidContext()) }
    }

/** Every Koin module of the app, in one place so tests can check the whole graph. */
val allModules = listOf(appModule, connectionModule, connectionFeatureModule)
