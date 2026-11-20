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
import io.github.ratmux.sdhelper.feature.home.HomeScreen
import io.github.ratmux.sdhelper.feature.home.defaultSurfaceEntries
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object ComputersRoute : NavKey

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
                            // The control panel and the editor arrive in Phase 3c; until then both lead to the
                            // computers screen, which shows the connection state.
                            when (surface.id) {
                                SurfaceIds.DECK, SurfaceIds.CONNECTION -> backStack.add(ComputersRoute)
                            }
                        },
                    )
                }
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
