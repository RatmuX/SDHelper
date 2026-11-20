package io.github.ratmux.sdhelper.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ratmux.sdhelper.ui.designapi.DeckScaffold
import io.github.ratmux.sdhelper.ui.designapi.DeckSwitchRow
import org.koin.compose.viewmodel.koinViewModel

/** App-wide options. Layouts and ready-made modes are in the shortcut editor. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    DeckScaffold(title = stringResource(R.string.settings_title), modifier = modifier, onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckSwitchRow(
                stringResource(R.string.settings_haptic),
                settings.hapticFeedback,
                viewModel::setHapticFeedback,
                Modifier.fillMaxWidth(),
            )
            DeckSwitchRow(
                stringResource(R.string.settings_keep_screen_on),
                settings.keepScreenOn,
                viewModel::setKeepScreenOn,
                Modifier.fillMaxWidth(),
            )
        }
    }
}
