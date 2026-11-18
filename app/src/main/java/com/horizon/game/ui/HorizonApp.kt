package com.horizon.game.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.horizon.game.ui.screens.CatalogScreen
import com.horizon.game.ui.screens.ControllerScreen
import com.horizon.game.ui.screens.GameDetailScreen
import com.horizon.game.ui.screens.HelpScreen
import com.horizon.game.ui.screens.HomeScreen
import com.horizon.game.ui.screens.LibraryScreen
import com.horizon.game.ui.screens.ProfileScreen
import com.horizon.game.ui.screens.SettingsScreen
import com.horizon.game.ui.screens.SplashScreen
import com.horizon.game.ui.theme.HorizonBlue

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CATALOG = "catalog"
    const val PROFILE = "profile"
    const val CONTROLLER = "controller"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val GAME = "game/{id}"
    fun game(id: String) = "game/$id"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "Accueil", Icons.Filled.Home),
    BottomItem(Routes.LIBRARY, "Bibliothèque", Icons.Filled.VideoLibrary),
    BottomItem(Routes.CATALOG, "Catalogue", Icons.Filled.Search),
    BottomItem(Routes.PROFILE, "Profil", Icons.Filled.Person)
)

@Composable
fun HorizonApp() {
    val navController = rememberNavController()
    val appState = remember { AppState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = HorizonBlue,
                                indicatorColor = HorizonBlue,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenGame = { navController.navigate(Routes.game(it)) },
                    onOpenLibrary = { navController.navigateTopLevel(Routes.LIBRARY) },
                    onOpenCatalog = { navController.navigateTopLevel(Routes.CATALOG) },
                    onOpenController = { navController.navigate(Routes.CONTROLLER) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(appState, onOpenGame = { navController.navigate(Routes.game(it)) })
            }
            composable(Routes.CATALOG) {
                CatalogScreen(appState, onOpenGame = { navController.navigate(Routes.game(it)) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenController = { navController.navigate(Routes.CONTROLLER) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenHelp = { navController.navigate(Routes.HELP) }
                )
            }
            composable(Routes.GAME) { entry ->
                GameDetailScreen(
                    gameId = entry.arguments?.getString("id").orEmpty(),
                    appState = appState,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.CONTROLLER) { ControllerScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(appState, onBack = { navController.popBackStack() }) }
            composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
