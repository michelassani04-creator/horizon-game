package com.horizon.game.data

import com.horizon.game.BuildConfig
import com.horizon.game.data.remote.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Télécharge un paquet de jeu et vérifie son empreinte SHA-256 avant de le garder. */
object PackDownloader {

    suspend fun download(
        url: String,
        dest: File,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://") && !(BuildConfig.DEBUG && url.startsWith("http://"))) {
            throw ApiException("insecure_url", "Adresse de téléchargement non sécurisée (HTTPS requis).")
        }
        val tmp = File(dest.path + ".part")
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            val status = conn.responseCode
            if (status != 200) throw ApiException("download_failed", "Téléchargement impossible (erreur $status).")
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                throw ApiException("bad_checksum", "Fichier corrompu ou modifié : l'empreinte ne correspond pas. Il n'a pas été gardé.")
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw ApiException("download_failed", "Impossible d'enregistrer le jeu sur l'appareil.")
        } catch (e: IOException) {
            throw ApiException("download_failed", "Téléchargement interrompu. Vérifie ta connexion et l'espace disponible.")
        } finally {
            conn?.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
