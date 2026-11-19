package com.horizon.game.ui.catalog

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.game.data.Prefs
import com.horizon.game.data.ServerConfig
import com.horizon.game.data.remote.ApiClient
import com.horizon.game.data.remote.ApiException
import com.horizon.game.data.remote.gameFromJson
import com.horizon.game.domain.model.Game
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

/**
 * Catalogue du HORIZON Store, lu depuis le serveur. La dernière réponse est gardée en cache
 * pour pouvoir afficher le Store hors-ligne. Aucun jeu n'est embarqué dans l'application.
 */
class CatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val api = ApiClient { ServerConfig.url(prefs) }

    var games by mutableStateOf(parse(prefs.string("catalog_cache", "")))
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            message = null
            try {
                val all = JSONArray()
                var page = 1
                while (page <= 20) {
                    val r = api.call("GET", "/v1/games?pageSize=100&page=$page") ?: break
                    val items = r.optJSONArray("items") ?: break
                    for (i in 0 until items.length()) all.put(items.getJSONObject(i))
                    if (all.length() >= r.optInt("total", 0) || items.length() == 0) break
                    page++
                }
                prefs.saveString("catalog_cache", all.toString())
                games = parse(all.toString())
            } catch (e: ApiException) {
                message = if (games.isEmpty()) e.message else "Hors-ligne : catalogue de la dernière connexion."
            } catch (e: JSONException) {
                message = "Réponse du serveur illisible."
            } finally {
                loading = false
            }
        }
    }

    private fun parse(raw: String): List<Game> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> runCatching { gameFromJson(arr.getJSONObject(i)) }.getOrNull() }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
