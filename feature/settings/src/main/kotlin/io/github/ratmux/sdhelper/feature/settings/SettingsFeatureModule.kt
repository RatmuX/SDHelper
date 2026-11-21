package io.github.ratmux.sdhelper.feature.settings

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of this feature's view models. */
val settingsFeatureModule =
    module {
        viewModel { SettingsViewModel(settings = get(), profiles = get()) }
    }
