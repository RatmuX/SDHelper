package io.github.ratmux.sdhelper

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.ratmux.sdhelper.feature.connection.AddComputerScreen
import io.github.ratmux.sdhelper.feature.connection.ComputersScreen
import io.github.ratmux.sdhelper.feature.deck.DeckScreen
import io.github.ratmux.sdhelper.feature.deckeditor.EditorScreen
import io.github.ratmux.sdhelper.feature.home.HomeScreen
import io.github.ratmux.sdhelper.feature.settings.GeneralSettingsScreen
import io.github.ratmux.sdhelper.feature.settings.SettingsHomeScreen
import io.github.ratmux.sdhelper.feature.settings.StreamDeckSettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object ComputersRoute : NavKey

@Serializable
data object DeckRoute : NavKey

@Serializable
data object EditorRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object StreamDeckSettingsRoute : NavKey

@Serializable
data object GeneralSettingsRoute : NavKey

@Serializable
data class AddComputerRoute(
    val host: String = "",
    val port: Int = 0,
) : NavKey

/** Screen routing lives here, in the composition root, so features never know about each other. */
@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeRoute)
    val back: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        onStartDeck = { backStack.add(DeckRoute) },
                        onEditDeck = { backStack.add(EditorRoute) },
                        onOpenComputers = { backStack.add(ComputersRoute) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                    )
                }
                entry<DeckRoute> {
                    DeckScreen()
                }
                entry<EditorRoute> { EditorScreen() }
                entry<SettingsRoute> {
                    SettingsHomeScreen(
                        onBack = back,
                        onOpenStreamDeck = { backStack.add(StreamDeckSettingsRoute) },
                        onOpenGeneral = { backStack.add(GeneralSettingsRoute) },
                    )
                }
                entry<StreamDeckSettingsRoute> { StreamDeckSettingsScreen(onBack = back) }
                entry<GeneralSettingsRoute> { GeneralSettingsScreen(onBack = back) }
                entry<ComputersRoute> {
                    ComputersScreen(
                        onBack = back,
                        onAddComputer = { host, port -> backStack.add(AddComputerRoute(host, port)) },
                    )
                }
                entry<AddComputerRoute> { key ->
                    AddComputerScreen(prefillHost = key.host, prefillPort = key.port, onBack = back, onDone = back)
                }
            },
    )
}
