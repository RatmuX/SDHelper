package io.github.ratmux.sdhelper.feature.deck

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of this feature's view models. */
val deckFeatureModule =
    module {
        viewModel { ActionsViewModel(deck = get(), status = get()) }
    }
