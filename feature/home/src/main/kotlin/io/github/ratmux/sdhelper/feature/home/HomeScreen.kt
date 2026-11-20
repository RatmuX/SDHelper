package io.github.ratmux.sdhelper.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ratmux.sdhelper.core.model.Surface
import io.github.ratmux.sdhelper.core.model.SurfaceIds
import io.github.ratmux.sdhelper.ui.designapi.DeckCard
import io.github.ratmux.sdhelper.ui.designapi.DeckScaffold

/** A surface plus the strings the home menu shows for it. New surfaces (keyboard, touchpad) add one entry. */
data class SurfaceEntry(
    val surface: Surface,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
)

/** Surfaces available in v1. */
fun defaultSurfaceEntries(): List<SurfaceEntry> =
    listOf(
        SurfaceEntry(Surface(SurfaceIds.DECK), R.string.surface_deck_title, R.string.surface_deck_subtitle),
        SurfaceEntry(
            Surface(SurfaceIds.DECK_EDITOR),
            R.string.surface_editor_title,
            R.string.surface_editor_subtitle,
        ),
        SurfaceEntry(
            Surface(SurfaceIds.CONNECTION),
            R.string.surface_connection_title,
            R.string.surface_connection_subtitle,
        ),
    )

@Composable
fun HomeScreen(
    entries: List<SurfaceEntry>,
    onOpen: (Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeckScaffold(title = stringResource(R.string.home_title), modifier = modifier) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            entries.forEach { entry ->
                DeckCard(
                    title = stringResource(entry.titleRes),
                    subtitle = stringResource(entry.subtitleRes),
                    onClick = { onOpen(entry.surface) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
