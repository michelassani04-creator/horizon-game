package com.horizon.game.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Erreur renvoyée par le serveur HORIZON ou liée à la connexion (message affichable à l'utilisateur). */
class ApiException(val code: String, override val message: String) : Exception(message)

/**
 * Client HTTP minimal pour l'API HORIZON (JSON). Utilise HttpURLConnection et org.json,
 * déjà inclus dans Android : aucune dépendance externe à ce stade.
 */
class ApiClient(private val baseUrl: () -> String) {

    suspend fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject? =
        withContext(Dispatchers.IO) {
            val base = baseUrl().trim().trimEnd('/')
            if (base.isEmpty()) {
                throw ApiException("no_server", "Serveur non configuré. Renseigne son adresse dans Réglages › Serveur.")
            }
            if (!base.startsWith("https://") && !base.startsWith("http://")) {
                throw ApiException("bad_url", "Adresse du serveur invalide : elle doit commencer par https://")
            }
            val conn = try {
                URL(base + path).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw ApiException("bad_url", "Adresse du serveur invalide.")
            }
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/json")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status in 200..299) {
                    if (text.isBlank()) null else JSONObject(text)
                } else {
                    val err = runCatching { JSONObject(text) }.getOrNull()
                    val code = err?.optString("error")?.takeIf { it.isNotBlank() } ?: "http_$status"
                    val msg = err?.optString("message")?.takeIf { it.isNotBlank() } ?: "Erreur du serveur ($status)."
                    throw ApiException(code, msg)
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: JSONException) {
                throw ApiException("bad_response", "Réponse du serveur illisible.")
            } catch (e: IOException) {
                throw ApiException("network", "Serveur injoignable. Vérifie ta connexion et l'adresse du serveur (HTTPS requis).")
            } finally {
                conn.disconnect()
            }
        }
}

fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
