package io.github.ratmux.sdhelper.feature.connection

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of this feature's view models. */
val connectionFeatureModule =
    module {
        viewModel {
            ComputersViewModel(
                store = get(),
                pairing = get(),
                network = get(),
                status = get(),
                discovery = get(),
            )
        }
        viewModel { AddComputerViewModel(pairing = get(), store = get(), network = get()) }
    }
