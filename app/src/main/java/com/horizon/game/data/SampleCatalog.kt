package com.horizon.game.data

import com.horizon.game.domain.model.Compatibility
import com.horizon.game.domain.model.Game
import com.horizon.game.domain.model.GameCategory

/**
 * Catalogue de démonstration (Lot 1). Titres fictifs, sans visuels ni contenus sous licence.
 * Il sera remplacé par GET /v1/games au Lot 3 (exigence F-013).
 */
object SampleCatalog {
    val games: List<Game> = listOf(
        Game(
            id = "horizon-runner",
            title = "Horizon Runner",
            publisher = "Studio démo",
            category = GameCategory.ACTION,
            description = "Jeu de course d'obstacles en vue latérale. Entrée fictive pour tester les fiches et les filtres.",
            ageRating = "3+",
            sizeMb = 180,
            compatibility = Compatibility.DECLARED
        ),
        Game(
            id = "cite-neon",
            title = "Cité Néon",
            publisher = "Studio démo",
            category = GameCategory.ADVENTURE,
            description = "Exploration d'une ville futuriste. Entrée fictive de démonstration.",
            ageRating = "12+",
            sizeMb = 640,
            compatibility = Compatibility.UNKNOWN
        ),
        Game(
            id = "course-des-rapides",
            title = "Course des Rapides",
            publisher = "Studio démo",
            category = GameCategory.RACING,
            description = "Course de bateaux sur un fleuve tropical. Entrée fictive de démonstration.",
            ageRating = "3+",
            sizeMb = 320,
            compatibility = Compatibility.VERIFIED
        ),
        Game(
            id = "orbite-zero",
            title = "Orbite Zéro",
            publisher = "Studio démo",
            category = GameCategory.PUZZLE,
            description = "Puzzle de mécanique orbitale. Entrée fictive de démonstration.",
            ageRating = "3+",
            sizeMb = 95,
            compatibility = Compatibility.VERIFIED
        ),
        Game(
            id = "forge-stellaire",
            title = "Forge Stellaire",
            publisher = "Studio démo",
            category = GameCategory.STRATEGY,
            description = "Gestion de colonies et de ressources. Entrée fictive de démonstration.",
            ageRating = "7+",
            sizeMb = 410,
            compatibility = Compatibility.DECLARED
        )
    )

    fun byId(id: String): Game? = games.firstOrNull { it.id == id }
}
