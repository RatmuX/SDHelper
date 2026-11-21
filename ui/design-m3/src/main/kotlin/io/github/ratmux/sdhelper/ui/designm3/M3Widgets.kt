package io.github.ratmux.sdhelper.ui.designm3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.ratmux.sdhelper.ui.designapi.DeckDrawer
import io.github.ratmux.sdhelper.ui.designapi.DeckIcon
import io.github.ratmux.sdhelper.ui.designapi.DeckRowEnd
import io.github.ratmux.sdhelper.ui.designapi.DeckSegment
import kotlinx.coroutines.launch

// Measurements of the owner's Material You 3 reference (dp). Colors, shapes and type come from MaterialTheme.
private val DRAWER_WIDTH = 220.dp
private val ROW_MIN_HEIGHT = 64.dp
private val CHOICE_MIN_HEIGHT = 58.dp
private val SEGMENT_HEIGHT = 42.dp
private val POPUP_ITEM_HEIGHT = 44.dp
private val DRAWER_ITEM_HEIGHT = 56.dp
private val ICON = 24.dp
private val SMALL_ICON = 18.dp
private const val SELECTED_SUMMARY_ALPHA = 0.76f
private const val LABEL_SPACING = 0.5f

/** The color of a secondary line of text. */
@Composable
private fun variant(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3TopBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = navigationIcon,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
internal fun M3DrawerShell(
    title: String,
    drawer: DeckDrawer,
    onItemClick: (String) -> Unit,
    onComputerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeThen: (() -> Unit) -> Unit = { action ->
        scope.launch { state.close() }
        action()
    }
    ModalNavigationDrawer(
        drawerState = state,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(DRAWER_WIDTH),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawerShape =
                    MaterialTheme.shapes.extraLarge.copy(
                        topStart =
                            androidx.compose.foundation.shape
                                .CornerSize(0.dp),
                        bottomStart =
                            androidx.compose.foundation.shape
                                .CornerSize(0.dp),
                    ),
            ) {
                DrawerContent(drawer, { id ->
                    closeThen {
                        onItemClick(id)
                    }
                }, { closeThen(onComputerClick) }, { closeThen(onSettingsClick) })
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                M3TopBar(title) {
                    IconButton(onClick = { scope.launch { state.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.design_menu))
                    }
                }
            },
            content = content,
        )
    }
}

@Composable
private fun DrawerContent(
    drawer: DeckDrawer,
    onItemClick: (String) -> Unit,
    onComputerClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 16.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckIcon("computer", null, scheme.primary, Modifier.size(ICON))
            Text(drawer.brand, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
        }
        Spacer(Modifier.height(34.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DRAWER_ITEM_HEIGHT)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(role = Role.Button, onClick = onComputerClick)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckIcon("computer", null, scheme.onSurface, Modifier.size(ICON))
            Column(Modifier.weight(1f)) {
                Text(
                    drawer.computerTitle,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                drawer.computerSubtitle?.let { address ->
                    Text(
                        address,
                        style = MaterialTheme.typography.labelMedium,
                        color = variant(),
                        maxLines = 1,
                    )
                }
            }
            if (drawer.computerSubtitle == null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = variant())
            }
        }
        HorizontalDivider(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            color = scheme.outlineVariant,
        )
        Text(
            drawer.sectionLabel,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = LABEL_SPACING.sp),
            color = variant(),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        drawer.items.forEach { item ->
            val available = item.badge == null
            val container = if (item.selected) scheme.secondaryContainer else Color.Transparent
            val content = if (item.selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DRAWER_ITEM_HEIGHT)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(container)
                    .then(
                        if (available) {
                            Modifier.selectable(
                                selected = item.selected,
                                role = Role.Tab,
                                onClick = { onItemClick(item.id) },
                            )
                        } else {
                            // not available yet: no click handler, no ripple, announced as unavailable
                            Modifier.semantics {
                                disabled()
                                stateDescription = item.badge.orEmpty()
                            }
                        },
                    ).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckIcon(item.iconKey, null, content, Modifier.size(ICON))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                item.badge?.let { note ->
                    Text(note, style = MaterialTheme.typography.labelMedium, color = variant(), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(DRAWER_ITEM_HEIGHT)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(role = Role.Button, onClick = onSettingsClick)
                .semantics { contentDescription = drawer.settingsDescription },
            contentAlignment = Alignment.Center,
        ) {
            DeckIcon("settings", null, scheme.onSurface, Modifier.size(ICON))
        }
    }
}

@Composable
internal fun M3SettingsGroup(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Every row draws a line on its top edge; shifting the column up by that line hides the first one.
        Column(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val line = 1.dp.roundToPx()
                layout(placeable.width, placeable.height - line) { placeable.place(0, -line) }
            },
            content = content,
        )
    }
}

@Composable
internal fun M3SettingsRow(
    title: String,
    summary: String?,
    iconKey: String?,
    end: DeckRowEnd,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction =
        when {
            end is DeckRowEnd.Toggle -> {
                Modifier.toggleable(value = end.checked, role = Role.Switch, onValueChange = end.onChange)
            }

            onClick != null -> {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            }

            // inert: no click handler, no ripple; a badge is announced as the state
            else -> {
                Modifier.semantics {
                    if (end is DeckRowEnd.Badge) {
                        disabled()
                        stateDescription = end.text
                    }
                }
            }
        }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = scheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = ROW_MIN_HEIGHT,
                ).then(interaction)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconKey != null) DeckIcon(iconKey, null, scheme.onSurface, Modifier.size(ICON))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = variant())
            }
            when (end) {
                DeckRowEnd.None -> {
                    // nothing at the end
                }

                DeckRowEnd.Chevron -> {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = variant())
                }

                DeckRowEnd.Check -> {
                    DeckIcon("check", null, scheme.primary, Modifier.size(ICON))
                }

                is DeckRowEnd.Badge -> {
                    Text(end.text, style = MaterialTheme.typography.labelMedium, color = variant())
                }

                is DeckRowEnd.Toggle -> {
                    Switch(checked = end.checked, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
internal fun M3ChoiceRow(
    title: String,
    summary: String?,
    iconKey: String?,
    selected: Boolean,
    badge: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val content = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    val interaction =
        if (onClick != null && badge == null) {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        } else {
            Modifier.semantics {
                if (badge != null) {
                    disabled()
                    stateDescription = badge
                }
            }
        }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .heightIn(min = CHOICE_MIN_HEIGHT)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(
                        scheme.secondaryContainer,
                    )
                } else {
                    Modifier.border(BorderStroke(1.dp, scheme.outlineVariant), shape)
                },
            ).then(interaction)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconKey != null) DeckIcon(iconKey, null, content, Modifier.size(ICON))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = content)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) content.copy(alpha = SELECTED_SUMMARY_ALPHA) else variant(),
                )
            }
        }
        when {
            selected -> DeckIcon("check", null, scheme.primary, Modifier.size(20.dp))
            badge != null -> Text(badge, style = MaterialTheme.typography.labelMedium, color = variant())
        }
    }
}

@Composable
internal fun M3SegmentedChoice(
    options: List<DeckSegment>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        options.forEachIndexed { index, option ->
            val active = option.id == selectedId
            val shape =
                when (index) {
                    0 -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                    options.lastIndex -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    else -> RoundedCornerShape(0.dp)
                }
            val content = if (active) scheme.onSecondaryContainer else scheme.onSurface
            Row(
                Modifier
                    .weight(1f)
                    .offset(x = (-index).dp) // neighbours share one border line
                    .height(SEGMENT_HEIGHT)
                    .clip(shape)
                    .then(if (active) Modifier.background(scheme.secondaryContainer) else Modifier)
                    .border(BorderStroke(1.dp, if (active) scheme.secondaryContainer else scheme.outline), shape)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(option.id) }),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (option.iconKey != null) DeckIcon(option.iconKey, null, content, Modifier.size(SMALL_ICON))
                Text(option.label, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

@Composable
internal fun M3FeatureCard(
    iconKey: String,
    title: String,
    copy: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.heightIn(min = 172.dp).padding(20.dp)) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                DeckIcon(iconKey, null, scheme.onPrimaryContainer, Modifier.size(20.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            Text(copy, style = MaterialTheme.typography.bodyMedium, color = variant())
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAction, modifier = Modifier.padding(top = 10.dp)) {
                    Text(actionText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun M3Pill(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
internal fun M3PopupMenu(
    onDismiss: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val offset = with(density) { IntOffset((-16).dp.roundToPx(), 60.dp.roundToPx()) }
    Popup(
        alignment = Alignment.TopEnd,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier.widthIn(min = 178.dp, max = 220.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
internal fun M3PopupItem(
    label: String,
    iconKey: String?,
    selected: Boolean,
    chevron: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(POPUP_ITEM_HEIGHT)
            .then(if (selected) Modifier.background(scheme.secondaryContainer) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconKey != null) DeckIcon(iconKey, null, content, Modifier.size(SMALL_ICON))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = content)
    }
}

@Composable
internal fun M3PopupHeading(text: String) {
    Text(
        text,
        style =
            MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = LABEL_SPACING.sp,
            ),
        color = variant(),
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
    )
}
