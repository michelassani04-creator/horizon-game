package com.horizon.game.data

import com.horizon.game.BuildConfig

/**
 * Adresse du serveur HORIZON : celle intégrée à l'application à la compilation (automatique pour les joueurs).
 * Un réglage « développeur » (version de test) peut la remplacer ; vide = adresse intégrée.
 */
object ServerConfig {
    fun url(prefs: Prefs): String =
        prefs.string("server_url", "").ifBlank { BuildConfig.API_URL }.trim().trimEnd('/')
}
