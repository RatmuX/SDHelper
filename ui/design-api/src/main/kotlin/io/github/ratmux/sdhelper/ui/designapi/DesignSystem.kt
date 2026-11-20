package io.github.ratmux.sdhelper.ui.designapi

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Visual weight of a button. */
enum class DeckEmphasis { PRIMARY, SECONDARY }

/** How a button tile looks, as a meaning; every design language maps it to its own colors. */
enum class DeckTileTone { NORMAL, ACTIVE, WARNING, ALERT }

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

    /**
     * A large square button of the control panel. The caller decides its size through [modifier]. With a null
     * [onClick] the tile is only shown (editor); [highlighted] marks a drop target; a disabled tile is dimmed.
     */
    @Composable
    fun ButtonTile(
        title: String,
        iconKey: String?,
        toggled: Boolean?,
        label: String?,
        tone: DeckTileTone,
        enabled: Boolean,
        highlighted: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    )

    /** The full-screen background of a screen without an app bar (the control panel). */
    @Composable
    fun Background(
        modifier: Modifier,
        content: @Composable () -> Unit,
    )

    /** An empty grid cell as shown in the editor; [highlighted] marks a drop target. */
    @Composable
    fun EmptyTile(
        highlighted: Boolean,
        modifier: Modifier,
    )

    @Composable
    fun PageIndicator(
        count: Int,
        current: Int,
        modifier: Modifier,
    )

    /** A row with a label and a switch; the whole row toggles. */
    @Composable
    fun SwitchRow(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier,
    )

    /** A dialog with arbitrary scrollable [content]. [confirmText] and [dismissText] are optional buttons. */
    @Composable
    fun Modal(
        title: String,
        onDismiss: () -> Unit,
        confirmText: String?,
        onConfirm: (() -> Unit)?,
        dismissText: String?,
        content: @Composable ColumnScope.() -> Unit,
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

@Composable
fun DeckButtonTile(
    title: String,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
    toggled: Boolean? = null,
    label: String? = null,
    tone: DeckTileTone = DeckTileTone.NORMAL,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) = LocalDesignSystem.current.ButtonTile(title, iconKey, toggled, label, tone, enabled, highlighted, onClick, modifier)

@Composable
fun DeckBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = LocalDesignSystem.current.Background(modifier, content)

@Composable
fun DeckEmptyTile(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) = LocalDesignSystem.current.EmptyTile(highlighted, modifier)

@Composable
fun DeckPageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) = LocalDesignSystem.current.PageIndicator(count, current, modifier)

@Composable
fun DeckSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = LocalDesignSystem.current.SwitchRow(label, checked, onCheckedChange, modifier)

@Composable
fun DeckModal(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = LocalDesignSystem.current.Modal(title, onDismiss, confirmText, onConfirm, dismissText, content)
