package com.horizon.game.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.horizon.game.data.Prefs
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

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Routes.HOME, "Accueil", Icons.Filled.Home),
    NavItem(Routes.LIBRARY, "Bibliothèque", Icons.Filled.VideoLibrary),
    NavItem(Routes.CATALOG, "Catalogue", Icons.Filled.Search),
    NavItem(Routes.PROFILE, "Profil", Icons.Filled.Person)
)

@Composable
fun HorizonApp(resumeTick: Int) {
    val context = LocalContext.current
    val view = LocalView.current
    val navController = rememberNavController()
    val appState = remember { AppState(Prefs(context)) }

    // Paysage ou grand écran : navigation latérale et accueil en deux colonnes.
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val wide = landscape || configuration.screenWidthDp >= 600

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showNav = currentRoute in navItems.map { it.route }

    // Détection des jeux au démarrage et à chaque retour dans l'application
    // (après avoir fermé un jeu ou installé une application).
    LaunchedEffect(resumeTick) { appState.refreshInstalled(context) }

    // Mode immersif en paysage : les barres système se révèlent d'un balayage.
    LaunchedEffect(landscape) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (landscape) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showNav && !wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    navItems.forEach { item ->
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
        Row(Modifier.padding(innerPadding)) {
            if (showNav && wide) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    navItems.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            alwaysShowLabel = false,
                            colors = NavigationRailItemDefaults.colors(
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
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
                modifier = Modifier.weight(1f)
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
                        appState = appState,
                        wide = wide,
                        onOpenGame = { navController.navigate(Routes.game(it)) },
                        onOpenLibrary = { navController.navigateTopLevel(Routes.LIBRARY) },
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
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
