package com.horizon.game.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.horizon.game.data.remote.UserInfo
import org.json.JSONObject

/**
 * Conserve le jeton de session et le profil en cache dans un stockage chiffré (Android Keystore).
 * Si le chiffrement est indisponible sur l'appareil, la session reste en mémoire seulement
 * (jamais écrite en clair sur le disque).
 */
class SessionStore(context: Context) {
    private val sp: SharedPreferences? = try {
        val key = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "horizon_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    private var memToken: String? = null
    private var memUser: String? = null

    fun token(): String? = sp?.getString("token", null) ?: memToken

    fun user(): UserInfo? {
        val raw = sp?.getString("user", null) ?: memUser ?: return null
        return runCatching { UserInfo.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun save(token: String, user: UserInfo) {
        memToken = token
        memUser = user.toJson().toString()
        sp?.edit()?.putString("token", token)?.putString("user", memUser)?.apply()
    }

    fun saveUser(user: UserInfo) {
        memUser = user.toJson().toString()
        sp?.edit()?.putString("user", memUser)?.apply()
    }

    fun clear() {
        memToken = null
        memUser = null
        sp?.edit()?.clear()?.apply()
    }
}
