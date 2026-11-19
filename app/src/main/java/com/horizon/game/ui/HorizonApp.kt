package com.horizon.game.ui

import android.app.Activity
import android.content.Context
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.horizon.game.ui.components.OsSwitchRow
import com.horizon.game.ui.components.osClickable
import com.horizon.game.ui.screens.ControllerScreen
import com.horizon.game.ui.screens.GameDetailScreen
import com.horizon.game.ui.screens.HelpScreen
import com.horizon.game.ui.screens.HomeScreen
import com.horizon.game.ui.screens.LibraryScreen
import com.horizon.game.ui.screens.ProfileScreen
import com.horizon.game.ui.screens.SettingsScreen
import com.horizon.game.ui.screens.SplashScreen
import com.horizon.game.ui.screens.StoreScreen
import com.horizon.game.ui.theme.HorizonBackground
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonSurface
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val STORE = "store"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val CONTROLLER = "controller"
    const val HELP = "help"
    const val AUTH = "auth"
    const val GAME = "game/{id}"
    fun game(id: String) = "game/$id"
}

private data class DockItem(val route: String, val label: String, val icon: ImageVector)

private val dockItems = listOf(
    DockItem(Routes.HOME, "Accueil", Icons.Filled.Home),
    DockItem(Routes.STORE, "Store", Icons.Filled.Storefront),
    DockItem(Routes.LIBRARY, "Jeux", Icons.Filled.VideoLibrary),
    DockItem(Routes.PROFILE, "Profil", Icons.Filled.Person),
    DockItem(Routes.SETTINGS, "Réglages", Icons.Filled.Settings)
)

private fun sectionTitle(route: String?): String = when {
    route == Routes.HOME -> "Accueil"
    route == Routes.STORE -> "Store"
    route == Routes.LIBRARY -> "Bibliothèque"
    route == Routes.PROFILE -> "Profil"
    route == Routes.SETTINGS -> "Réglages"
    route == Routes.CONTROLLER -> "Manette"
    route == Routes.HELP -> "Aide"
    route == Routes.AUTH -> "HORIZON ID"
    route != null && route.startsWith("game/") -> "Fiche du jeu"
    else -> ""
}

/**
 * Coque « système » de HORIZON GAME : barre d'état, dock latéral, centre de contrôle,
 * et zone de contenu. L'application est verrouillée en paysage (manifeste).
 */
@Composable
fun HorizonApp(resumeTick: Int) {
    val context = LocalContext.current
    val view = LocalView.current
    val navController = rememberNavController()
    val appState = remember { AppState(Prefs(context)) }
    val auth: com.horizon.game.ui.auth.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val inShell = route != null && route != Routes.SPLASH
    val showDock = route in dockItems.map { it.route }
    var panelOpen by remember { mutableStateOf(false) }
    val status = rememberStatus()

    // Détection des jeux du Store au démarrage et à chaque retour dans l'application,
    // et mode immersif : les barres Android se révèlent d'un balayage depuis le bord.
    LaunchedEffect(resumeTick) {
        appState.refreshInstalled(context)
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = panelOpen) { panelOpen = false }

    Box(Modifier.fillMaxSize().background(HorizonBackground)) {
        com.horizon.game.ui.components.HorizonBackdrop(
            accent = appState.accent.value,
            reduce = appState.reduceAnimations.value
        )
        Column(
            Modifier
                .fillMaxSize()
                .then(if (inShell) Modifier.safeDrawingPadding() else Modifier)
        ) {
            if (inShell) {
                StatusBar(
                    title = sectionTitle(route),
                    status = status,
                    onOpenPanel = { panelOpen = true }
                )
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (showDock) {
                    Dock(currentRoute = route, onNavigate = { navController.navigateTopLevel(it) })
                }
                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.weight(1f).fillMaxHeight()
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
                            onOpenGame = { navController.navigate(Routes.game(it)) },
                            onOpenStore = { navController.navigateTopLevel(Routes.STORE) }
                        )
                    }
                    composable(Routes.STORE) {
                        StoreScreen(appState, onOpenGame = { navController.navigate(Routes.game(it)) })
                    }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            appState,
                            onOpenGame = { navController.navigate(Routes.game(it)) },
                            onOpenStore = { navController.navigateTopLevel(Routes.STORE) }
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            auth = auth,
                            onOpenAuth = { navController.navigate(Routes.AUTH) },
                            onOpenController = { navController.navigate(Routes.CONTROLLER) },
                            onOpenHelp = { navController.navigate(Routes.HELP) }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(appState, auth, onOpenController = { navController.navigate(Routes.CONTROLLER) })
                    }
                    composable(Routes.GAME) { entry ->
                        GameDetailScreen(
                            gameId = entry.arguments?.getString("id").orEmpty(),
                            appState = appState,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.AUTH) {
                        com.horizon.game.ui.screens.AuthScreen(
                            auth = auth,
                            onDone = { navController.popBackStack() },
                            onOpenServerSettings = { navController.navigateTopLevel(Routes.SETTINGS) }
                        )
                    }
                    composable(Routes.CONTROLLER) { ControllerScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
                }
            }
        }

        if (inShell) {
            // Fond assombri : un clic ou la touche Retour ferme le centre de contrôle.
            AnimatedVisibility(visible = panelOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { panelOpen = false }
                )
            }
            AnimatedVisibility(
                visible = panelOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                ControlCenter(
                    appState = appState,
                    onOpenSettings = {
                        panelOpen = false
                        navController.navigateTopLevel(Routes.SETTINGS)
                    },
                    onOpenController = {
                        panelOpen = false
                        navController.navigate(Routes.CONTROLLER)
                    },
                    onClose = { panelOpen = false }
                )
            }
        }
    }
}

@Composable
private fun rememberStatus(): String {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val battery = try {
                (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) {
                -1
            }
            text = if (battery in 1..100) "$time   ·   $battery %" else time
            delay(20_000)
        }
    }
    return text
}

@Composable
private fun StatusBar(title: String, status: String, onOpenPanel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(HorizonSurface.copy(alpha = 0.55f))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("HORIZON", color = HorizonOnSurface, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp)
        Text(" GAME", color = HorizonBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(1.dp).height(18.dp).background(HorizonOnSurfaceMuted.copy(alpha = 0.5f)))
        Spacer(Modifier.width(14.dp))
        Text(title, color = HorizonOnSurfaceMuted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text(status, color = HorizonOnSurface, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onOpenPanel) {
            Icon(Icons.Filled.Tune, contentDescription = "Centre de contrôle", tint = HorizonOnSurface)
        }
    }
}

@Composable
private fun Dock(currentRoute: String?, onNavigate: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(84.dp)
            .background(HorizonSurface.copy(alpha = 0.55f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        dockItems.forEach { item ->
            val selected = currentRoute == item.route
            Column(
                Modifier
                    .width(68.dp)
                    .osClickable(RoundedCornerShape(14.dp)) { onNavigate(item.route) }
                    .background(if (selected) HorizonBlue.copy(alpha = 0.22f) else Color.Transparent)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) HorizonBlue else HorizonOnSurfaceMuted
                )
                Text(
                    item.label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) HorizonOnSurface else HorizonOnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun ControlCenter(
    appState: AppState,
    onOpenSettings: () -> Unit,
    onOpenController: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(HorizonSurface)
            .clickable(enabled = false, onClick = {})
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Centre de contrôle", style = MaterialTheme.typography.titleLarge, color = HorizonOnSurface)
        OsSwitchRow("Notifications", appState.notificationsEnabled.value) { appState.setNotifications(it) }
        OsSwitchRow("Réduire les animations", appState.reduceAnimations.value) { appState.setReduceAnimations(it) }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onOpenController, modifier = Modifier.fillMaxWidth()) { Text("Manette") }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Tous les réglages") }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
