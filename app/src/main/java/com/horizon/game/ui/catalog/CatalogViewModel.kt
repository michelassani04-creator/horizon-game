package com.horizon.game.ui.catalog

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.game.data.Prefs
import com.horizon.game.data.ServerConfig
import com.horizon.game.data.auth.SessionStore
import com.horizon.game.data.remote.ApiClient
import com.horizon.game.data.remote.ApiException
import com.horizon.game.data.remote.gameFromJson
import com.horizon.game.data.remote.stringOrNull
import com.horizon.game.domain.model.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** État d'une commande en cours pour un jeu : creating, pending, failed, timeout ou error. */
class OrderUi(val orderId: String, val status: String, val message: String?)

/**
 * Catalogue du HORIZON Store, lu depuis le serveur. La dernière réponse est gardée en cache
 * pour pouvoir afficher le Store hors-ligne. Aucun jeu n'est embarqué dans l'application.
 */
class CatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val api = ApiClient { ServerConfig.url(prefs) }
    private val session = SessionStore(app)

    var games by mutableStateOf(parse(prefs.string("catalog_cache", "")))
        private set
    /** Jeux possédés par le joueur connecté (avec leurs liens de téléchargement). */
    var owned by mutableStateOf(parse(if (session.token() != null) prefs.string("owned_cache", "") else ""))
        private set
    /** Commandes en cours, par identifiant de jeu. */
    val orders = mutableStateMapOf<String, OrderUi>()
    private val orderJobs = HashMap<String, Job>()

    var redeemBusy by mutableStateOf(false)
        private set
    var redeemError by mutableStateOf<String?>(null)
        private set
    var redeemedGame by mutableStateOf<Game?>(null)
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
                refreshOwned()
            } catch (e: ApiException) {
                message = if (games.isEmpty()) e.message else "Hors-ligne : catalogue de la dernière connexion."
            } catch (e: JSONException) {
                message = "Réponse du serveur illisible."
            } finally {
                loading = false
            }
        }
    }

    /** Recharge la bibliothèque du joueur ; vide s'il n'est pas connecté. */
    private suspend fun refreshOwned() {
        val token = session.token()
        if (token == null) {
            owned = emptyList()
            prefs.saveString("owned_cache", "")
            return
        }
        try {
            val items = api.call("GET", "/v1/me/games", token = token)?.optJSONArray("items") ?: JSONArray()
            prefs.saveString("owned_cache", items.toString())
            owned = parse(items.toString())
        } catch (e: ApiException) {
            if (e.code == "unauthorized") owned = emptyList()
            // Hors-ligne : on garde la bibliothèque de la dernière connexion.
        }
    }

    /**
     * Achète un jeu : crée la commande, puis attend que LE SERVEUR la confirme (jamais l'application).
     * Le paiement est pour l'instant un paiement de TEST, sans argent réel.
     */
    fun buy(game: Game) {
        val token = session.token() ?: return
        if (orderJobs[game.id]?.isActive == true) return
        orders[game.id] = OrderUi("", "creating", null)
        orderJobs[game.id] = viewModelScope.launch {
            try {
                val res = api.call("POST", "/v1/me/orders", JSONObject().put("gameId", game.id).put("provider", "sandbox"), token)
                    ?: throw ApiException("bad_response", "Réponse du serveur vide.")
                val id = res.getString("id")
                var status = res.getString("status")
                orders[game.id] = OrderUi(id, status, res.stringOrNull("message"))
                var tries = 0
                while (status == "pending" && tries < 100) {
                    delay(3_000)
                    tries++
                    val r = api.call("GET", "/v1/me/orders/$id", token = token) ?: break
                    status = r.getString("status")
                    orders[game.id] = OrderUi(id, status, orders[game.id]?.message)
                }
                when (status) {
                    "paid" -> {
                        refreshOwned()
                        orders.remove(game.id)
                    }
                    "pending" -> orders[game.id] = OrderUi(id, "timeout", "Paiement pas encore confirmé. Réessaie plus tard : la commande reste enregistrée.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                orders[game.id] = OrderUi("", "error", e.message)
            } catch (e: Exception) {
                orders[game.id] = OrderUi("", "error", "Réponse du serveur illisible.")
            }
        }
    }

    /** Jeu gratuit : ajout direct à la bibliothèque. */
    fun claimFree(game: Game) {
        val token = session.token() ?: return
        if (orderJobs[game.id]?.isActive == true) return
        orders[game.id] = OrderUi("", "creating", null)
        orderJobs[game.id] = viewModelScope.launch {
            try {
                api.call("POST", "/v1/me/games/${game.id}/claim", token = token)
                refreshOwned()
                orders.remove(game.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                orders[game.id] = OrderUi("", "error", e.message)
            } catch (e: Exception) {
                orders[game.id] = OrderUi("", "error", "Réponse du serveur illisible.")
            }
        }
    }

    fun dismissOrder(gameId: String) {
        orderJobs[gameId]?.cancel()
        orders.remove(gameId)
    }

    fun clearRedeem() {
        redeemError = null
        redeemedGame = null
    }

    /** Utilise un code de téléchargement : le jeu est ajouté à la bibliothèque. */
    fun redeem(code: String) {
        if (redeemBusy) return
        viewModelScope.launch {
            redeemBusy = true
            redeemError = null
            redeemedGame = null
            try {
                val res = api.call("POST", "/v1/me/redeem", JSONObject().put("code", code.trim()), session.token())
                val game = res?.let { gameFromJson(it) }
                refreshOwned()
                redeemedGame = game
            } catch (e: ApiException) {
                redeemError = e.message
            } catch (e: Exception) {
                redeemError = "Réponse du serveur illisible."
            } finally {
                redeemBusy = false
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
