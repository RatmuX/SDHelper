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
        viewModel {
            val context = androidContext()
            LayoutsViewModel(
                profiles = get(),
                language = {
                    context.resources.configuration.locales[0]
                        .language
                },
                defaultPageName = { number -> context.getString(R.string.editor_default_page, number) },
                newLayoutName = { context.getString(R.string.layouts_default_name) },
            )
        }
    }
