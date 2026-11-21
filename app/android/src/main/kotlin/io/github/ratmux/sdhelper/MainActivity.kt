package io.github.ratmux.sdhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.SettingsStore
import io.github.ratmux.sdhelper.ui.designapi.DeckTheme
import org.koin.android.ext.android.inject

/** Composition root: the only place that knows which design language is active. */
class MainActivity : ComponentActivity() {
    private val settingsStore: SettingsStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsStore.settings.collectAsState(initial = AppSettings())
            DeckTheme(selectedDesign, darkTheme = settings.themeMode.isDark(isSystemInDarkTheme())) {
                AppNavigation()
            }
        }
    }
}
