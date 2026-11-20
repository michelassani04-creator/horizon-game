package com.horizon.game.data

import android.content.Context
import java.io.File

/**
 * Paquets de jeu téléchargés. Ils sont rangés dans le dossier privé de l'application
 * (aucune permission de stockage) et disparaissent avec elle.
 */
class PackStore(private val context: Context, private val prefs: Prefs) {

    private fun root(): File {
        val base = context.getExternalFilesDir("packs") ?: File(context.filesDir, "packs")
        base.mkdirs()
        return base
    }

    private fun safeId(gameId: String): String {
        require(Regex("[a-z0-9][a-z0-9-]{1,59}").matches(gameId)) { "Identifiant de jeu invalide" }
        return gameId
    }

    fun file(gameId: String): File = File(root(), safeId(gameId) + ".pack")

    /** Version du paquet installé, 0 si le jeu n'est pas téléchargé. */
    fun installedVersion(gameId: String): Int {
        if (!file(gameId).exists()) return 0
        return prefs.string("pack_ver_$gameId", "0").toIntOrNull() ?: 0
    }

    fun markInstalled(gameId: String, versionCode: Int) {
        prefs.saveString("pack_ver_$gameId", versionCode.toString())
    }

    fun delete(gameId: String) {
        file(gameId).delete()
        File(root(), safeId(gameId) + ".pack.part").delete()
        prefs.saveString("pack_ver_$gameId", "0")
    }
}
