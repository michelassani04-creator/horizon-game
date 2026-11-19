package com.horizon.game.ui.auth

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.game.data.Prefs
import com.horizon.game.data.ServerConfig
import com.horizon.game.data.auth.SessionStore
import com.horizon.game.data.remote.ApiClient
import com.horizon.game.data.remote.ApiException
import com.horizon.game.data.remote.UserInfo
import kotlinx.coroutines.launch
import org.json.JSONObject

/** État et actions de HORIZON ID : inscription, connexion, session, vérification de l'e-mail. */
class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val store = SessionStore(app)
    private val api = ApiClient { serverUrl }

    var serverUrl by mutableStateOf(ServerConfig.url(prefs))
        private set
    var user by mutableStateOf(store.user())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<String?>(null)
        private set

    /** Adresse saisie à la main (version de test) ; vide = adresse intégrée à l'application. */
    val serverOverride: String get() = prefs.string("server_url", "")

    val isLoggedIn: Boolean get() = user != null

    init {
        // Rafraîchit le profil en arrière-plan ; en cas de session expirée on déconnecte, hors-ligne on garde le cache.
        val token = store.token()
        if (token != null && serverUrl.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val me = api.call("GET", "/v1/me", token = token)
                    if (me != null) UserInfo.fromJson(me).also { user = it; store.saveUser(it) }
                } catch (e: ApiException) {
                    if (e.code == "unauthorized") clearSession()
                }
            }
        }
    }

    fun clearMessages() {
        error = null
        info = null
    }

    fun saveServerUrl(value: String) {
        prefs.saveString("server_url", value.trim().trimEnd('/'))
        serverUrl = ServerConfig.url(prefs)
        clearMessages()
    }

    fun testServer() = launchAction {
        val cfg = api.call("GET", "/v1/config")
        info = "Serveur joignable (API v${cfg?.optString("apiVersion", "?") ?: "?"})."
    }

    fun register(email: String, phone: String, password: String, name: String, onDone: () -> Unit) = launchAction {
        val body = JSONObject()
            .put("password", password)
            .put("displayName", name.trim())
            .put("deviceLabel", Build.MODEL)
        if (email.isNotBlank()) body.put("email", email.trim())
        if (phone.isNotBlank()) body.put("phone", phone.trim())
        startSession(api.call("POST", "/v1/auth/register", body))
        onDone()
    }

    fun login(identifier: String, password: String, onDone: () -> Unit) = launchAction {
        val body = JSONObject()
            .put("identifier", identifier.trim())
            .put("password", password)
            .put("deviceLabel", Build.MODEL)
        startSession(api.call("POST", "/v1/auth/login", body))
        onDone()
    }

    fun logout() {
        val token = store.token()
        clearSession()
        if (token != null) {
            viewModelScope.launch {
                try {
                    api.call("POST", "/v1/auth/logout", token = token)
                } catch (_: ApiException) {
                    // Déconnexion locale déjà faite ; la session serveur expirera d'elle-même.
                }
            }
        }
    }

    fun requestEmailCode() = launchAction {
        api.call("POST", "/v1/me/verify/request", JSONObject().put("channel", "email"), store.token())
        info = "Code demandé. Saisis-le pour vérifier ton adresse e-mail."
    }

    fun confirmEmailCode(code: String, onDone: () -> Unit) = launchAction {
        val me = api.call("POST", "/v1/me/verify/confirm", JSONObject().put("channel", "email").put("code", code.trim()), store.token())
        if (me != null) UserInfo.fromJson(me).also { user = it; store.saveUser(it) }
        info = "Adresse e-mail vérifiée."
        onDone()
    }

    private fun startSession(response: JSONObject?) {
        response ?: throw ApiException("bad_response", "Réponse du serveur vide.")
        val token = response.getString("token")
        val u = UserInfo.fromJson(response.getJSONObject("user"))
        store.save(token, u)
        user = u
    }

    private fun clearSession() {
        store.clear()
        user = null
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            info = null
            try {
                block()
            } catch (e: ApiException) {
                if (e.code == "unauthorized") clearSession()
                error = e.message
            } finally {
                busy = false
            }
        }
    }
}
