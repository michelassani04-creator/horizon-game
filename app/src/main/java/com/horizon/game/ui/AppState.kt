package com.horizon.game.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.horizon.game.data.GameScanner
import com.horizon.game.data.Prefs
import com.horizon.game.domain.model.Game

/**
 * État d'application. Favoris, jeux récents et réglages sont conservés localement (Prefs) ;
 * la synchronisation avec le compte arrive au Lot 3. Favoris et récents contiennent des
 * identifiants de jeux du Store.
 */
class AppState(private val prefs: Prefs) {

    val favorites: SnapshotStateList<String> =
        mutableStateListOf<String>().apply { addAll(prefs.favorites()) }

    /** Jeux lancés récemment, du plus récent au plus ancien. */
    val recent: SnapshotStateList<String> =
        mutableStateListOf<String>().apply { addAll(prefs.recent()) }

    /** Paquets des jeux du Store trouvés sur l'appareil. */
    var installedPackages by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Catalogue du Store reçu du serveur (ou gardé en cache hors-ligne). Vide tant qu'aucun serveur n'est joignable. */
    var catalog by mutableStateOf<List<Game>>(emptyList())
        private set

    fun updateCatalog(games: List<Game>) {
        catalog = games
    }

    fun gameById(id: String): Game? = catalog.firstOrNull { it.id == id }

    val notificationsEnabled = mutableStateOf(prefs.bool("notifications", true))
    val reduceAnimations = mutableStateOf(prefs.bool("reduce_animations", false))

    /** Couleur d'ambiance du fond animé (jeu sélectionné sur l'accueil), null = bleu HORIZON. */
    val accent = mutableStateOf<androidx.compose.ui.graphics.Color?>(null)

    fun isInstalled(game: Game): Boolean =
        game.packageName?.let { it in installedPackages } == true

    /** Jeux du Store installés sur cet appareil : le contenu de la bibliothèque. */
    val installedGames: List<Game>
        get() = catalog.filter { isInstalled(it) }

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

    suspend fun refreshInstalled(context: Context) {
        val storePackages = catalog.mapNotNull { it.packageName }.toSet()
        installedPackages = GameScanner.installedStorePackages(context, storePackages)
    }

    fun launchGame(context: Context, game: Game) {
        val pkg = game.packageName
        if (pkg != null && GameScanner.launch(context, pkg)) {
            recent.remove(game.id)
            recent.add(0, game.id)
            while (recent.size > 10) recent.removeAt(recent.size - 1)
            prefs.saveRecent(recent.toList())
        } else {
            Toast.makeText(context, "Impossible de lancer ce jeu.", Toast.LENGTH_SHORT).show()
        }
    }
}
