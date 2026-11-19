package com.horizon.game.data

import com.horizon.game.domain.model.Compatibility
import com.horizon.game.domain.model.Game
import com.horizon.game.domain.model.GameCategory

/**
 * Catalogue du HORIZON Store, version de démonstration (Lot 2).
 * Titres fictifs, sans visuels ni contenus sous licence. Les paquets listés ici sont
 * les seuls que l'application cherche sur l'appareil.
 * Il sera remplacé par GET /v1/games au Lot 3 (exigence F-013).
 *
 * Pour tester « Installé » et « Jouer » : remplace le packageName d'une entrée par celui
 * d'une application réellement installée sur ton téléphone.
 */
object SampleCatalog {
    val games: List<Game> = listOf(
        Game(
            id = "horizon-runner",
            title = "Horizon Runner",
            publisher = "Studio démo",
            category = GameCategory.ACTION,
            description = "Jeu de course d'obstacles en vue latérale. Entrée fictive pour tester le Store.",
            ageRating = "3+",
            sizeMb = 180,
            compatibility = Compatibility.DECLARED,
            packageName = "com.horizon.demo.runner",
            featured = true
        ),
        Game(
            id = "cite-neon",
            title = "Cité Néon",
            publisher = "Studio démo",
            category = GameCategory.ADVENTURE,
            description = "Exploration d'une ville futuriste. Entrée fictive de démonstration.",
            ageRating = "12+",
            sizeMb = 640,
            compatibility = Compatibility.UNKNOWN,
            packageName = "com.horizon.demo.neon"
        ),
        Game(
            id = "course-des-rapides",
            title = "Course des Rapides",
            publisher = "Studio démo",
            category = GameCategory.RACING,
            description = "Course de bateaux sur un fleuve tropical. Entrée fictive de démonstration.",
            ageRating = "3+",
            sizeMb = 320,
            compatibility = Compatibility.VERIFIED,
            packageName = "com.horizon.demo.rapides"
        ),
        Game(
            id = "orbite-zero",
            title = "Orbite Zéro",
            publisher = "Studio démo",
            category = GameCategory.PUZZLE,
            description = "Puzzle de mécanique orbitale. Entrée fictive de démonstration.",
            ageRating = "3+",
            sizeMb = 95,
            compatibility = Compatibility.VERIFIED,
            packageName = "com.horizon.demo.orbite"
        ),
        Game(
            id = "forge-stellaire",
            title = "Forge Stellaire",
            publisher = "Studio démo",
            category = GameCategory.STRATEGY,
            description = "Gestion de colonies et de ressources. Entrée fictive de démonstration.",
            ageRating = "7+",
            sizeMb = 410,
            compatibility = Compatibility.DECLARED,
            packageName = "com.horizon.demo.forge"
        ),
        Game(
            id = "tir-de-precision",
            title = "Tir de Précision",
            publisher = "Studio démo",
            category = GameCategory.ACTION,
            description = "Défis de tir chronométrés. Entrée fictive de démonstration.",
            ageRating = "12+",
            sizeMb = 220,
            compatibility = Compatibility.DECLARED,
            packageName = "com.horizon.demo.tir"
        )
    )

    fun byId(id: String): Game? = games.firstOrNull { it.id == id }
}
