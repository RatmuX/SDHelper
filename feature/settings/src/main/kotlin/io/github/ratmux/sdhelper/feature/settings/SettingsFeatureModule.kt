package io.github.ratmux.sdhelper.feature.settings

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of this feature's view models. */
val settingsFeatureModule =
    module {
        viewModel {
            val context: Context = androidContext()
            SettingsViewModel(
                settings = get(),
                profiles = get(),
                language = {
                    context.resources.configuration.locales[0]
                        .language
                },
            )
        }
    }
