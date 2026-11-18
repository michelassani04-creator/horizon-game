package com.horizon.game.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.horizon.game.data.GameScanner
import com.horizon.game.data.InstalledGame
import com.horizon.game.data.Prefs

/**
 * État d'application. Favoris, jeux récents et réglages sont conservés
 * localement (Prefs) ; la synchronisation serveur arrive au Lot 3.
 * Les favoris et récents contiennent soit un id de catalogue, soit un nom de paquet Android.
 */
class AppState(private val prefs: Prefs) {

    val favorites: SnapshotStateList<String> =
        mutableStateListOf<String>().apply { addAll(prefs.favorites()) }

    /** Paquets lancés récemment, du plus récent au plus ancien. */
    val recent: SnapshotStateList<String> =
        mutableStateListOf<String>().apply { addAll(prefs.recent()) }

    var installed by mutableStateOf<List<InstalledGame>>(emptyList())
        private set

    val notificationsEnabled = mutableStateOf(prefs.bool("notifications", true))
    val reduceAnimations = mutableStateOf(prefs.bool("reduce_animations", false))
    val showAllApps = mutableStateOf(prefs.bool("show_all_apps", false))

    /** Jeux détectés ; toutes les applications lançables si l'option de test est active. */
    val visibleInstalled: List<InstalledGame>
        get() = if (showAllApps.value) installed else installed.filter { it.isGame }

    fun isFavorite(id: String): Boolean = favorites.contains(id)

    fun toggleFavorite(id: String) {
        if (!favorites.remove(id)) favorites.add(id)
        prefs.saveFavorites(favorites.toList())
    }

    fun setNotifications(value: Boolean) {
        notificationsEnabled.value = value
        prefs.saveBool("notifications", value)
    }

    fun setReduceAnimations(value: Boolean) {
        reduceAnimations.value = value
        prefs.saveBool("reduce_animations", value)
    }

    fun setShowAllApps(value: Boolean) {
        showAllApps.value = value
        prefs.saveBool("show_all_apps", value)
    }

    suspend fun refreshInstalled(context: Context) {
        installed = GameScanner.scan(context)
    }

    fun launchGame(context: Context, packageName: String) {
        if (GameScanner.launch(context, packageName)) {
            recent.remove(packageName)
            recent.add(0, packageName)
            while (recent.size > 10) recent.removeAt(recent.size - 1)
            prefs.saveRecent(recent.toList())
        } else {
            Toast.makeText(context, "Impossible de lancer ce jeu.", Toast.LENGTH_SHORT).show()
        }
    }
}
