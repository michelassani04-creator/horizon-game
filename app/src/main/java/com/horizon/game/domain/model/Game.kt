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

data class Game(
    val id: String,
    val title: String,
    val publisher: String,
    val category: GameCategory,
    val description: String,
    val ageRating: String,
    val sizeMb: Int,
    val compatibility: Compatibility,
    /** Nom de paquet Android, renseigné en Lot 2 pour détecter/lancer le jeu. */
    val packageName: String? = null
)
