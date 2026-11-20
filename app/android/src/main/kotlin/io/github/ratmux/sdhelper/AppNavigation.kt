package io.github.ratmux.sdhelper

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.ratmux.sdhelper.core.model.SurfaceIds
import io.github.ratmux.sdhelper.feature.connection.AddComputerScreen
import io.github.ratmux.sdhelper.feature.connection.ComputersScreen
import io.github.ratmux.sdhelper.feature.deck.DeckScreen
import io.github.ratmux.sdhelper.feature.deckeditor.EditorScreen
import io.github.ratmux.sdhelper.feature.home.HomeScreen
import io.github.ratmux.sdhelper.feature.home.defaultSurfaceEntries
import io.github.ratmux.sdhelper.feature.settings.SettingsScreen
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
                        entries = defaultSurfaceEntries(),
                        onOpen = { surface ->
                            when (surface.id) {
                                SurfaceIds.DECK -> backStack.add(DeckRoute)
                                SurfaceIds.DECK_EDITOR -> backStack.add(EditorRoute)
                                SurfaceIds.CONNECTION -> backStack.add(ComputersRoute)
                                SurfaceIds.SETTINGS -> backStack.add(SettingsRoute)
                            }
                        },
                    )
                }
                entry<DeckRoute> {
                    DeckScreen(onOpenComputers = { backStack.add(ComputersRoute) })
                }
                entry<EditorRoute> { EditorScreen() }
                entry<SettingsRoute> { SettingsScreen(onBack = back) }
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
