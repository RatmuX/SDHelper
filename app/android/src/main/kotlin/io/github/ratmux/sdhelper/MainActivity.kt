package io.github.ratmux.sdhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ratmux.sdhelper.feature.home.HomeScreen
import io.github.ratmux.sdhelper.feature.home.defaultSurfaceEntries
import io.github.ratmux.sdhelper.ui.designapi.DeckTheme

/** Composition root: the only place that knows which design language is active. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeckTheme(selectedDesign) {
                // Navigation to the surfaces arrives in Phase 3.
                HomeScreen(entries = defaultSurfaceEntries(), onOpen = {})
            }
        }
    }
}
