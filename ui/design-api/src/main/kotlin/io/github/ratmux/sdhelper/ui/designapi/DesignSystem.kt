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
enum class DeckTextStyle { TITLE, BODY, CAPTION, EYEBROW, HEADLINE, SUPPORTING, SECTION }

/** What sits at the end of a settings row. */
sealed interface DeckRowEnd {
    data object None : DeckRowEnd

    /** The row opens something. */
    data object Chevron : DeckRowEnd

    /** The row is the selected one. */
    data object Check : DeckRowEnd

    /** A short note such as "coming soon". */
    data class Badge(
        val text: String,
    ) : DeckRowEnd

    /** A switch; the whole row toggles. */
    data class Toggle(
        val checked: Boolean,
        val onChange: (Boolean) -> Unit,
    ) : DeckRowEnd
}

/** One option of a segmented choice (for example light or dark). */
data class DeckSegment(
    val id: String,
    val label: String,
    val iconKey: String? = null,
)

/** One entry of the navigation drawer. An entry with a [badge] is not available yet and does not react to touch. */
data class DeckDrawerItem(
    val id: String,
    val label: String,
    val iconKey: String,
    val selected: Boolean = false,
    val badge: String? = null,
)

/** Everything the navigation drawer shows; see [DesignSystem.DrawerShell]. */
data class DeckDrawer(
    val brand: String,
    val computerTitle: String,
    /** The address of a connected computer; null while no computer is connected. */
    val computerSubtitle: String?,
    val sectionLabel: String,
    val items: List<DeckDrawerItem>,
    /** Spoken name of the icon-only settings entry at the bottom. */
    val settingsDescription: String,
)

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
    fun Theme(
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Scaffold(
        title: String,
        modifier: Modifier,
        onBack: (() -> Unit)?,
        content: @Composable (PaddingValues) -> Unit,
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
        number: Int?,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    )

    /** The full-screen background of a screen without an app bar (the control panel). */
    @Composable
    fun Background(
        modifier: Modifier,
        content: @Composable () -> Unit,
    )

    /** An empty grid cell as shown in the editor, numbered like the buttons; [highlighted] marks a drop target. */
    @Composable
    fun EmptyTile(
        highlighted: Boolean,
        number: Int?,
        modifier: Modifier,
    )

    /** Dots for the pages plus an optional [label] such as "Apps · 1 / 2". */
    @Composable
    fun PageIndicator(
        count: Int,
        current: Int,
        label: String?,
        modifier: Modifier,
    )

    /**
     * The home frame: a top bar with a menu button that opens the navigation [drawer]. [onItemClick] gets the id of a
     * drawer item that is available; [onComputerClick] and [onSettingsClick] are the two other entries of the drawer.
     */
    @Composable
    fun DrawerShell(
        title: String,
        drawer: DeckDrawer,
        onItemClick: (String) -> Unit,
        onComputerClick: () -> Unit,
        onSettingsClick: () -> Unit,
        modifier: Modifier,
        content: @Composable (PaddingValues) -> Unit,
    )

    /** A rounded surface that groups [SettingsRow]s and choices, rows separated by thin lines. */
    @Composable
    fun SettingsGroup(
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit,
    )

    /** A row of a settings list. With a null [onClick] (and no toggle) the row is inert: no reaction at all. */
    @Composable
    fun SettingsRow(
        title: String,
        summary: String?,
        iconKey: String?,
        end: DeckRowEnd,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    )

    /** A selectable option inside a [SettingsGroup]. A [badge] marks it as not available: it ignores touch. */
    @Composable
    fun ChoiceRow(
        title: String,
        summary: String?,
        iconKey: String?,
        selected: Boolean,
        badge: String?,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    )

    @Composable
    fun SegmentedChoice(
        options: List<DeckSegment>,
        selectedId: String,
        onSelect: (String) -> Unit,
        modifier: Modifier,
    )

    /** A card with an icon, a title, a sentence and one text action at the bottom end. */
    @Composable
    fun FeatureCard(
        iconKey: String,
        title: String,
        copy: String,
        actionText: String,
        onAction: () -> Unit,
        modifier: Modifier,
    )

    /** A rounded button with a dropdown arrow, for menus. [expanded] flips the arrow. */
    @Composable
    fun Pill(
        text: String,
        expanded: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
    )

    /** A small menu that floats at the top end of the screen; [onDismiss] when the person taps outside. */
    @Composable
    fun PopupMenu(
        onDismiss: () -> Unit,
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit,
    )

    @Composable
    fun PopupItem(
        label: String,
        iconKey: String?,
        selected: Boolean,
        chevron: Boolean,
        onClick: () -> Unit,
    )

    @Composable
    fun PopupHeading(text: String)

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

/** Applies [design] to [content]; [darkTheme] picks the dark palette. */
@Composable
fun DeckTheme(
    design: DesignSystem,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDesignSystem provides design) {
        design.Theme(darkTheme, content)
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
    number: Int? = null,
    onClick: (() -> Unit)? = null,
) = LocalDesignSystem.current.ButtonTile(
    title,
    iconKey,
    toggled,
    label,
    tone,
    enabled,
    highlighted,
    number,
    onClick,
    modifier,
)

@Composable
fun DeckBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = LocalDesignSystem.current.Background(modifier, content)

@Composable
fun DeckEmptyTile(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    number: Int? = null,
) = LocalDesignSystem.current.EmptyTile(highlighted, number, modifier)

@Composable
fun DeckPageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) = LocalDesignSystem.current.PageIndicator(count, current, label, modifier)

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

@Composable
fun DeckDrawerShell(
    title: String,
    drawer: DeckDrawer,
    onItemClick: (String) -> Unit,
    onComputerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) = LocalDesignSystem.current.DrawerShell(
    title,
    drawer,
    onItemClick,
    onComputerClick,
    onSettingsClick,
    modifier,
    content,
)

@Composable
fun DeckSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = LocalDesignSystem.current.SettingsGroup(modifier, content)

@Composable
fun DeckSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    iconKey: String? = null,
    end: DeckRowEnd = DeckRowEnd.None,
    onClick: (() -> Unit)? = null,
) = LocalDesignSystem.current.SettingsRow(title, summary, iconKey, end, onClick, modifier)

@Composable
fun DeckChoiceRow(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    summary: String? = null,
    iconKey: String? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
) = LocalDesignSystem.current.ChoiceRow(title, summary, iconKey, selected, badge, onClick, modifier)

@Composable
fun DeckSegmentedChoice(
    options: List<DeckSegment>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) = LocalDesignSystem.current.SegmentedChoice(options, selectedId, onSelect, modifier)

@Composable
fun DeckFeatureCard(
    iconKey: String,
    title: String,
    copy: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) = LocalDesignSystem.current.FeatureCard(iconKey, title, copy, actionText, onAction, modifier)

@Composable
fun DeckPill(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LocalDesignSystem.current.Pill(text, expanded, onClick, modifier)

@Composable
fun DeckPopupMenu(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = LocalDesignSystem.current.PopupMenu(onDismiss, modifier, content)

@Composable
fun DeckPopupItem(
    label: String,
    onClick: () -> Unit,
    iconKey: String? = null,
    selected: Boolean = false,
    chevron: Boolean = false,
) = LocalDesignSystem.current.PopupItem(label, iconKey, selected, chevron, onClick)

@Composable
fun DeckPopupHeading(text: String) = LocalDesignSystem.current.PopupHeading(text)
