package io.github.ratmux.sdhelper.feature.deckeditor

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of this feature's view models. */
val deckEditorFeatureModule =
    module {
        viewModel {
            val context = androidContext()
            EditorViewModel(
                profiles = get(),
                cache = get(),
                deck = get(),
                status = get(),
                defaultPageName = { number -> context.getString(R.string.editor_default_page, number) },
            )
        }
    }
