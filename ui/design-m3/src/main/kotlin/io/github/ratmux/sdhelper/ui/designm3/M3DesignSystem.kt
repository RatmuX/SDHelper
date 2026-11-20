package io.github.ratmux.sdhelper.ui.designm3

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ratmux.sdhelper.ui.designapi.DeckEmphasis
import io.github.ratmux.sdhelper.ui.designapi.DeckIcon
import io.github.ratmux.sdhelper.ui.designapi.DeckTextStyle
import io.github.ratmux.sdhelper.ui.designapi.DeckTileTone
import io.github.ratmux.sdhelper.ui.designapi.DeckTone
import io.github.ratmux.sdhelper.ui.designapi.DesignSystem

/** Material 3 implementation of the design contract. Dynamic color on Android 12+, static palette below. */
object M3DesignSystem : DesignSystem {
    @Composable
    override fun Theme(content: @Composable () -> Unit) {
        val dark = isSystemInDarkTheme()
        val colors =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }

                dark -> {
                    darkColorScheme()
                }

                else -> {
                    lightColorScheme()
                }
            }
        MaterialTheme(colorScheme = colors, content = content)
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
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.design_back),
                                )
                            }
                        }
                    },
                )
            },
            content = content,
        )
    }

    @Composable
    override fun Card(
        title: String,
        subtitle: String?,
        onClick: () -> Unit,
        modifier: Modifier,
    ) {
        Card(onClick = onClick, modifier = modifier) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    @Composable
    override fun Button(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        emphasis: DeckEmphasis,
    ) {
        when (emphasis) {
            DeckEmphasis.PRIMARY -> {
                Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
            }

            DeckEmphasis.SECONDARY -> {
                OutlinedButton(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                ) { Text(text) }
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
            }
        Text(text = text, style = textStyle, modifier = modifier)
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
        onClick: (() -> Unit)?,
        modifier: Modifier,
    ) {
        val (container, content) = tileColors(tone, MaterialTheme.colorScheme)
        val border = if (highlighted) BorderStroke(HIGHLIGHT_WIDTH, MaterialTheme.colorScheme.primary) else null
        val dim = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)
        val body: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxSize().padding(TILE_PADDING),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DeckIcon(iconKey, toggled, content, Modifier.fillMaxWidth(ICON_FRACTION).aspectRatio(1f))
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 1)
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
        modifier: Modifier,
    ) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = if (highlighted) scheme.primaryContainer.copy(alpha = HIGHLIGHT_FILL) else Color.Transparent,
            border =
                BorderStroke(
                    if (highlighted) HIGHLIGHT_WIDTH else EMPTY_WIDTH,
                    if (highlighted) scheme.primary else scheme.outlineVariant,
                ),
        ) {}
    }

    @Composable
    override fun PageIndicator(
        count: Int,
        current: Int,
        modifier: Modifier,
    ) {
        val description = stringResource(R.string.design_page_indicator, current + 1, count)
        Row(
            modifier = modifier.semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { index ->
                val color =
                    if (index ==
                        current
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(color))
            }
        }
    }

    @Composable
    override fun SwitchRow(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier,
    ) {
        Row(
            modifier =
                modifier
                    .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                    .padding(vertical = ROW_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }

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
        scheme: ColorScheme,
    ): Pair<Color, Color> =
        when (tone) {
            DeckTileTone.NORMAL -> scheme.surfaceContainerHigh to scheme.onSurface
            DeckTileTone.ACTIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
            DeckTileTone.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            DeckTileTone.ALERT -> scheme.errorContainer to scheme.onErrorContainer
        }

    private const val DISABLED_ALPHA = 0.38f
    private const val ICON_FRACTION = 0.42f
    private const val HIGHLIGHT_FILL = 0.4f
    private val HIGHLIGHT_WIDTH = 3.dp
    private val EMPTY_WIDTH = 1.dp
    private val TILE_PADDING = 8.dp
    private val DOT_SIZE = 8.dp
    private val DOT_GAP = 6.dp
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
