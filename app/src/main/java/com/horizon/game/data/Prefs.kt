package com.horizon.game.data

import android.content.Context

/**
 * Stockage local simple (SharedPreferences) pour favoris, jeux récents et réglages.
 * Il sera remplacé par Room + synchronisation serveur au Lot 3 (F-012).
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("horizon_prefs", Context.MODE_PRIVATE)

    fun favorites(): List<String> = readList("favorites")
    fun saveFavorites(list: List<String>) = writeList("favorites", list)

    fun recent(): List<String> = readList("recent")
    fun saveRecent(list: List<String>) = writeList("recent", list)

    fun bool(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)
    fun saveBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    private fun readList(key: String): List<String> =
        (sp.getString(key, "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun writeList(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString("\n")).apply()
    }
}
