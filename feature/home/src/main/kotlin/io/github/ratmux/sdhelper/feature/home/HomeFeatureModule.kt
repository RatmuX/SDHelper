package io.github.ratmux.sdhelper.feature.home

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of this feature's view models. */
val homeFeatureModule =
    module {
        viewModel { HomeViewModel(settings = get(), status = get(), computers = get()) }
    }
