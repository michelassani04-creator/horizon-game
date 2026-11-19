package com.horizon.game.domain.model

/** Niveau de fiabilité de l'information de compatibilité (cahier des charges §4.1). */
enum class Compatibility(val label: String) {
    VERIFIED("Vérifiée"),
    DECLARED("Déclarée par l'éditeur"),
    UNKNOWN("Inconnue")
}

/** Catégories de jeux (vision HORIZON §2). */
enum class GameCategory(val label: String) {
    ACTION("Action"),
    RACING("Course"),
    PUZZLE("Réflexion"),
    ADVENTURE("Aventure"),
    STRATEGY("Stratégie")
}

/** Jeu publié sur le HORIZON Store. */
data class Game(
    val id: String,
    val title: String,
    val publisher: String,
    val category: GameCategory,
    val description: String,
    val ageRating: String,
    val sizeMb: Int,
    val compatibility: Compatibility,
    /** Nom de paquet Android du jeu : sert à savoir s'il est installé et à le lancer. */
    val packageName: String? = null,
    /** Adresse de téléchargement de l'APK et son empreinte SHA-256 (vérifiée avant installation). */
    val apkUrl: String? = null,
    val apkSha256: String? = null,
    /** Mis en avant dans la bannière du Store. */
    val featured: Boolean = false
)
