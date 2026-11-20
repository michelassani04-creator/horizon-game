package com.horizon.game.ui.downloads

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.game.data.PackDownloader
import com.horizon.game.data.PackStore
import com.horizon.game.data.Prefs
import com.horizon.game.data.remote.ApiException
import com.horizon.game.domain.model.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Gestionnaire de téléchargements des paquets de jeu. Les téléchargements continuent quand on change
 * d'écran, mais s'arrêtent si l'application est fermée.
 */
class DownloadsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PackStore(app, Prefs(app))
    private val jobs = HashMap<String, Job>()

    /** Progression de 0 à 1 par jeu ; absent = pas de téléchargement en cours ; négatif = taille inconnue. */
    val progress = mutableStateMapOf<String, Float>()
    val errors = mutableStateMapOf<String, String>()

    /** Change à chaque ajout ou suppression de paquet, pour rafraîchir l'affichage. */
    var tick by mutableIntStateOf(0)
        private set

    fun installedVersion(gameId: String): Int {
        tick // lecture volontaire : redessine l'écran quand un paquet change
        return store.installedVersion(gameId)
    }

    fun start(game: Game) {
        val url = game.packUrl
        val sha = game.packSha256
        if (url == null || sha == null) {
            errors[game.id] = "Ce jeu n'a pas encore de paquet à télécharger."
            return
        }
        if (jobs[game.id]?.isActive == true) return
        errors.remove(game.id)
        progress[game.id] = 0f
        jobs[game.id] = viewModelScope.launch {
            try {
                PackDownloader.download(url, store.file(game.id), sha) { done, total ->
                    progress[game.id] = if (total > 0) done.toFloat() / total.toFloat() else -1f
                }
                store.markInstalled(game.id, game.versionCode)
                tick++
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                errors[game.id] = e.message
            } catch (e: Exception) {
                errors[game.id] = "Erreur pendant le téléchargement."
            } finally {
                progress.remove(game.id)
            }
        }
    }

    fun cancel(gameId: String) {
        jobs[gameId]?.cancel()
    }

    fun delete(gameId: String) {
        jobs[gameId]?.cancel()
        store.delete(gameId)
        errors.remove(gameId)
        tick++
    }
}
