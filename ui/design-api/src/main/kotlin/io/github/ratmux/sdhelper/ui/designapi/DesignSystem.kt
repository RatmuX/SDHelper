package io.github.ratmux.sdhelper.ui.designapi

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Visual weight of a button. */
enum class DeckEmphasis { PRIMARY, SECONDARY }

/** Typographic role of a piece of text. */
enum class DeckTextStyle { TITLE, BODY, CAPTION }

/** Meaning of an inline message; the design language decides colors and icons. */
enum class DeckTone { NEUTRAL, POSITIVE, WARNING, ERROR }

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
        onBack: (() -> Unit)?,
        content: @Composable (PaddingValues) -> Unit,
    )

    @Composable
    fun Card(
        title: String,
        subtitle: String?,
        onClick: () -> Unit,
        modifier: Modifier,
    )

    @Composable
    fun Button(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        emphasis: DeckEmphasis,
    )

    @Composable
    fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier,
        numeric: Boolean,
        isError: Boolean,
        enabled: Boolean,
    )

    @Composable
    fun ListItem(
        title: String,
        supporting: String?,
        trailing: String?,
        enabled: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
    )

    @Composable
    fun Dialog(
        title: String,
        text: String,
        confirmText: String,
        onConfirm: () -> Unit,
        dismissText: String?,
        onDismiss: () -> Unit,
    )

    @Composable
    fun StatusMessage(
        text: String,
        tone: DeckTone,
        modifier: Modifier,
    )

    @Composable
    fun Progress(modifier: Modifier)

    @Composable
    fun Text(
        text: String,
        style: DeckTextStyle,
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

/** Screen frame with a title bar; pass [onBack] to show a back button. */
@Composable
fun DeckScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) = LocalDesignSystem.current.Scaffold(title, modifier, onBack, content)

@Composable
fun DeckCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) = LocalDesignSystem.current.Card(title, subtitle, onClick, modifier)

@Composable
fun DeckButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: DeckEmphasis = DeckEmphasis.PRIMARY,
) = LocalDesignSystem.current.Button(text, onClick, modifier, enabled, emphasis)

@Composable
fun DeckTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    isError: Boolean = false,
    enabled: Boolean = true,
) = LocalDesignSystem.current.TextField(value, onValueChange, label, modifier, numeric, isError, enabled)

@Composable
fun DeckListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
) = LocalDesignSystem.current.ListItem(title, supporting, trailing, enabled, onClick, modifier)

@Composable
fun DeckDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null,
) = LocalDesignSystem.current.Dialog(title, text, confirmText, onConfirm, dismissText, onDismiss)

@Composable
fun DeckStatusMessage(
    text: String,
    modifier: Modifier = Modifier,
    tone: DeckTone = DeckTone.NEUTRAL,
) = LocalDesignSystem.current.StatusMessage(text, tone, modifier)

/** Indeterminate progress bar. */
@Composable
fun DeckProgress(modifier: Modifier = Modifier) = LocalDesignSystem.current.Progress(modifier)

@Composable
fun DeckText(
    text: String,
    modifier: Modifier = Modifier,
    style: DeckTextStyle = DeckTextStyle.BODY,
) = LocalDesignSystem.current.Text(text, style, modifier)
