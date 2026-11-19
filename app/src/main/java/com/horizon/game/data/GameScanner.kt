package com.horizon.game.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vérifie lesquels des jeux du HORIZON Store sont installés sur l'appareil.
 * Seuls les noms de paquet du Store sont retenus : les autres applications ne sont ni
 * lues (ni nom, ni icône), ni conservées, ni affichées.
 */
object GameScanner {

    suspend fun installedStorePackages(context: Context, storePackages: Set<String>): Set<String> =
        withContext(Dispatchers.IO) {
            if (storePackages.isEmpty()) return@withContext emptySet<String>()
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            context.packageManager.queryIntentActivities(query, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it in storePackages }
                .toSet()
        }

    /** Demande à Android d'ouvrir le jeu (F-008). */
    fun launch(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
