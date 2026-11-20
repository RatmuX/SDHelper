package io.github.ratmux.sdhelper.ui.designapi

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Contract between feature code and a concrete design language (Material 3, Miuix, ...).
 * Features call the `Deck*` functions below and never import a design library.
 * See docs/DESIGN_SYSTEM.md.
 */
@Stable
interface DesignSystem {
    @Composable
    fun Theme(content: @Composable () -> Unit)

    @Composable
    fun Scaffold(
        title: String,
        modifier: Modifier,
        content: @Composable (PaddingValues) -> Unit,
    )

    @Composable
    fun Card(
        title: String,
        subtitle: String?,
        onClick: () -> Unit,
        modifier: Modifier,
    )
}

val LocalDesignSystem =
    staticCompositionLocalOf<DesignSystem> {
        error("No DesignSystem provided. Wrap the UI in DeckTheme(design) { ... }.")
    }

@Composable
fun DeckTheme(
    design: DesignSystem,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDesignSystem provides design) {
        design.Theme(content)
    }
}

@Composable
fun DeckScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) = LocalDesignSystem.current.Scaffold(title, modifier, content)

@Composable
fun DeckCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) = LocalDesignSystem.current.Card(title, subtitle, onClick, modifier)
