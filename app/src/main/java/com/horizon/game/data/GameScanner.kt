package com.horizon.game.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Application Android lançable détectée sur l'appareil (F-005). */
data class InstalledGame(
    val packageName: String,
    val label: String,
    /** true si Android la déclare comme jeu (catégorie « jeu »). */
    val isGame: Boolean,
    val icon: ImageBitmap?
)

object GameScanner {

    /**
     * Liste les applications lançables (intent MAIN/LAUNCHER, déclaré dans <queries>
     * du manifeste : pas de permission QUERY_ALL_PACKAGES).
     */
    suspend fun scan(context: Context): List<InstalledGame> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val result = LinkedHashMap<String, InstalledGame>()
        for (info in pm.queryIntentActivities(query, 0)) {
            val appInfo = info.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName
            if (pkg == context.packageName || result.containsKey(pkg)) continue
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            val icon = try {
                info.loadIcon(pm).toBitmap(160, 160).asImageBitmap()
            } catch (e: Exception) {
                null
            }
            result[pkg] = InstalledGame(pkg, label, isGame(appInfo), icon)
        }
        result.values.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun isGame(info: ApplicationInfo): Boolean =
        info.category == ApplicationInfo.CATEGORY_GAME ||
            (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    /** Demande à Android d'ouvrir l'application cible (F-008). */
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
