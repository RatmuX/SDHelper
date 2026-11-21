package io.github.ratmux.sdhelper.ui.designm3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ratmux.sdhelper.ui.designapi.DeckDrawer
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckIcon
import io.github.ratmux.sdhelper.ui.designapi.DeckRowEnd
import io.github.ratmux.sdhelper.ui.designapi.DeckSegment
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTileTone
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import io.github.ratmux.sdhelper.ui.designapi.DesignSystem

/** Material 3 implementation of the design contract, in the owner's Material You 3 look (see [M3Palette]). */
object M3DesignSystem : DesignSystem {
    @Composable
    override fun Theme(
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) M3Palette.dark else M3Palette.light, content = content)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Scaffold(
        title: String,
        modifier: Modifier,
        onBack: (() -> Unit)?,
        content: @Composable (PaddingValues) -> Unit,
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                M3TopBar(title) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.design_back),
                            )
                        }
                    }
                }
            },
            content = content,
        )
    }

    @Composable
    override fun Button(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        emphasis: DeckEmphasis,
    ) {
        // the design has pills in the secondary container and plain text actions, no filled or outlined buttons
        when (emphasis) {
            DeckEmphasis.PRIMARY -> {
                FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
            }

            DeckEmphasis.SECONDARY -> {
                TextButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
            }
        }
    }

    @Composable
    override fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier,
        numeric: Boolean,
        isError: Boolean,
        enabled: Boolean,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier,
            singleLine = true,
            isError = isError,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        )
    }

    @Composable
    override fun ListItem(
        title: String,
        supporting: String?,
        trailing: String?,
        enabled: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
    ) {
        ListItem(
            // A disabled row is dimmed and ignores taps; the system announces it as disabled.
            modifier =
                modifier
                    .alpha(
                        if (enabled) 1f else DISABLED_ALPHA,
                    ).clickable(enabled = enabled, onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            supportingContent = supporting?.let { { Text(it) } },
            trailingContent = trailing?.let { { Text(it) } },
        ) {
            Text(title)
        }
    }

    @Composable
    override fun Dialog(
        title: String,
        text: String,
        confirmText: String,
        onConfirm: () -> Unit,
        dismissText: String?,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
            dismissButton = dismissText?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
        )
    }

    @Composable
    override fun StatusMessage(
        text: String,
        tone: DeckTone,
        modifier: Modifier,
    ) {
        val scheme = MaterialTheme.colorScheme
        val (container, content) = toneColors(tone, scheme)
        Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
    }

    @Composable
    override fun Progress(modifier: Modifier) {
        LinearProgressIndicator(modifier = modifier)
    }

    @Composable
    override fun Text(
        text: String,
        style: DeckTextStyle,
        modifier: Modifier,
    ) {
        val textStyle =
            when (style) {
                DeckTextStyle.TITLE -> MaterialTheme.typography.titleMedium
                DeckTextStyle.BODY -> MaterialTheme.typography.bodyMedium
                DeckTextStyle.CAPTION -> MaterialTheme.typography.bodySmall
                DeckTextStyle.EYEBROW -> MaterialTheme.typography.labelLarge
                DeckTextStyle.HEADLINE -> MaterialTheme.typography.headlineSmall
                DeckTextStyle.SUPPORTING -> MaterialTheme.typography.bodyLarge
                DeckTextStyle.SECTION -> MaterialTheme.typography.labelLarge
            }
        val color =
            when (style) {
                DeckTextStyle.EYEBROW, DeckTextStyle.SUPPORTING -> MaterialTheme.colorScheme.onSurfaceVariant
                DeckTextStyle.SECTION -> MaterialTheme.colorScheme.primary
                else -> Color.Unspecified
            }
        Text(text = text, style = textStyle, color = color, modifier = modifier)
    }

    @Composable
    override fun ButtonTile(
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
    ) {
        val scheme = MaterialTheme.colorScheme
        val editing = number != null
        val (container, content) = tileColors(tone, editing, scheme)
        val border =
            when {
                highlighted -> BorderStroke(HIGHLIGHT_WIDTH, scheme.primary)
                editing -> BorderStroke(EMPTY_WIDTH, scheme.outlineVariant)
                else -> null
            }
        val dim = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)
        val body: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(TILE_PADDING),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DeckIcon(iconKey, toggled, content, Modifier.fillMaxWidth(ICON_FRACTION).aspectRatio(1f))
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(top = LABEL_GAP),
                    )
                    if (label != null) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = content.copy(alpha = if (tone == DeckTileTone.NORMAL) 1f else HINT_ALPHA),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
                if (number != null) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = NUMBER_START, top = NUMBER_TOP),
                    )
                }
            }
        }
        if (onClick != null) {
            Surface(
                onClick = onClick,
                modifier = dim,
                enabled = enabled,
                shape = MaterialTheme.shapes.large,
                color = container,
                contentColor = content,
                border = border,
            ) { body() }
        } else {
            Surface(
                modifier = dim,
                shape = MaterialTheme.shapes.large,
                color = container,
                contentColor = content,
                border = border,
            ) {
                body()
            }
        }
    }

    @Composable
    override fun Background(
        modifier: Modifier,
        content: @Composable () -> Unit,
    ) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }

    @Composable
    override fun EmptyTile(
        highlighted: Boolean,
        number: Int?,
        modifier: Modifier,
    ) {
        val scheme = MaterialTheme.colorScheme
        val border =
            BorderStroke(
                if (highlighted) HIGHLIGHT_WIDTH else EMPTY_WIDTH,
                if (highlighted) scheme.primary else scheme.outlineVariant,
            )
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = if (highlighted) scheme.primaryContainer.copy(alpha = HIGHLIGHT_FILL) else scheme.surfaceContainer,
            border = border,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(TILE_PADDING),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DeckIcon("add", null, scheme.onSurfaceVariant, Modifier.fillMaxWidth(ICON_FRACTION).aspectRatio(1f))
                    Text(
                        stringResource(R.string.design_empty_key),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = LABEL_GAP),
                    )
                }
                if (number != null) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = NUMBER_START, top = NUMBER_TOP),
                    )
                }
            }
        }
    }

    @Composable
    override fun PageIndicator(
        count: Int,
        current: Int,
        label: String?,
        modifier: Modifier,
    ) {
        val description = stringResource(R.string.design_page_indicator, current + 1, count)
        Row(
            modifier = modifier.semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(DOT_LABEL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DOT_GAP), verticalAlignment = Alignment.CenterVertically) {
                repeat(count) { index ->
                    val active = index == current
                    Box(
                        Modifier
                            .size(width = if (active) ACTIVE_DOT_WIDTH else DOT_SIZE, height = DOT_SIZE)
                            .clip(CircleShape)
                            .background(dotColor(active)),
                    )
                }
            }
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun dotColor(active: Boolean): Color =
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    @Composable
    override fun DrawerShell(
        title: String,
        drawer: DeckDrawer,
        onItemClick: (String) -> Unit,
        onComputerClick: () -> Unit,
        onSettingsClick: () -> Unit,
        modifier: Modifier,
        content: @Composable (PaddingValues) -> Unit,
    ) = M3DrawerShell(title, drawer, onItemClick, onComputerClick, onSettingsClick, modifier, content)

    @Composable
    override fun SettingsGroup(
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) = M3SettingsGroup(modifier, content)

    @Composable
    override fun SettingsRow(
        title: String,
        summary: String?,
        iconKey: String?,
        end: DeckRowEnd,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    ) = M3SettingsRow(title, summary, iconKey, end, onClick, modifier)

    @Composable
    override fun ChoiceRow(
        title: String,
        summary: String?,
        iconKey: String?,
        selected: Boolean,
        badge: String?,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    ) = M3ChoiceRow(title, summary, iconKey, selected, badge, onClick, modifier)

    @Composable
    override fun SegmentedChoice(
        options: List<DeckSegment>,
        selectedId: String,
        onSelect: (String) -> Unit,
        modifier: Modifier,
    ) = M3SegmentedChoice(options, selectedId, onSelect, modifier)

    @Composable
    override fun FeatureCard(
        iconKey: String,
        title: String,
        copy: String,
        actionText: String,
        onAction: () -> Unit,
        modifier: Modifier,
    ) = M3FeatureCard(iconKey, title, copy, actionText, onAction, modifier)

    @Composable
    override fun Pill(
        text: String,
        expanded: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
    ) = M3Pill(text, expanded, onClick, modifier)

    @Composable
    override fun PopupMenu(
        onDismiss: () -> Unit,
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) = M3PopupMenu(onDismiss, modifier, content)

    @Composable
    override fun PopupItem(
        label: String,
        iconKey: String?,
        selected: Boolean,
        chevron: Boolean,
        onClick: () -> Unit,
    ) = M3PopupItem(label, iconKey, selected, chevron, onClick)

    @Composable
    override fun PopupHeading(text: String) = M3PopupHeading(text)

    @Composable
    override fun Modal(
        title: String,
        onDismiss: () -> Unit,
        confirmText: String?,
        onConfirm: (() -> Unit)?,
        dismissText: String?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
            confirmButton = {
                if (confirmText != null && onConfirm != null) TextButton(onClick = onConfirm) { Text(confirmText) }
            },
            dismissButton = dismissText?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
        )
    }

    private fun tileColors(
        tone: DeckTileTone,
        editing: Boolean,
        scheme: ColorScheme,
    ): Pair<Color, Color> =
        when (tone) {
            DeckTileTone.NORMAL -> normalTile(editing, scheme) to scheme.onSurface
            DeckTileTone.ACTIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
            DeckTileTone.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            DeckTileTone.ALERT -> scheme.errorContainer to scheme.onErrorContainer
        }

    private fun normalTile(
        editing: Boolean,
        scheme: ColorScheme,
    ): Color = if (editing) scheme.surfaceContainer else scheme.surfaceContainerLow

    private const val DISABLED_ALPHA = 0.38f
    private const val ICON_FRACTION = 0.2f
    private const val HIGHLIGHT_FILL = 0.4f
    private const val HINT_ALPHA = 0.72f
    private val HIGHLIGHT_WIDTH = 3.dp
    private val EMPTY_WIDTH = 1.dp
    private val TILE_PADDING = 12.dp
    private val LABEL_GAP = 10.dp
    private val NUMBER_START = 10.dp
    private val NUMBER_TOP = 8.dp
    private val DOT_SIZE = 8.dp
    private val ACTIVE_DOT_WIDTH = 20.dp
    private val DOT_GAP = 6.dp
    private val DOT_LABEL_GAP = 8.dp
    private val ROW_PADDING = 8.dp

    /** Container and content color of a tone, always taken as a matching pair from the scheme. */
    private fun toneColors(
        tone: DeckTone,
        scheme: ColorScheme,
    ): Pair<Color, Color> =
        when (tone) {
            DeckTone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurfaceVariant
            DeckTone.POSITIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
            DeckTone.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            DeckTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        }
}
