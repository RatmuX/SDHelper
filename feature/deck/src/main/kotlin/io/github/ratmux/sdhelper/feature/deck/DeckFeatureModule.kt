package io.github.ratmux.sdhelper.feature.deck

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of this feature's view models. */
val deckFeatureModule =
    module {
        viewModel {
            DeckViewModel(profiles = get(), cache = get(), deck = get(), status = get(), settings = get())
        }
    }
