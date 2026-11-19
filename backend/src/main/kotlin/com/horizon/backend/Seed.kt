package com.horizon.backend

import org.slf4j.LoggerFactory

/** Jeux fictifs de démonstration, insérés seulement si SEED_DEMO=true et que le catalogue est vide. */
object Seed {
    private val log = LoggerFactory.getLogger("Seed")

    fun run(store: Store) {
        if (store.countGames() > 0) return
        demoGames().forEach { store.upsertGame(it) }
        log.info("Catalogue de démonstration inséré")
    }

    private fun game(
        id: String, title: String, category: String, description: String, size: Int,
        featured: Boolean = false, age: String = "7+"
    ) = GameDto(
        id = id,
        title = title,
        publisher = "Studio Démo HORIZON",
        category = category,
        description = description,
        ageRating = age,
        sizeMb = size,
        compatibility = "DECLARED",
        packageName = "com.horizon.demo.${id.replace('-', '_')}",
        featured = featured
    )

    private fun demoGames() = listOf(
        game("horizon-runner", "Horizon Runner", "ACTION", "Course d'obstacles à grande vitesse au coucher du soleil.", 320, featured = true),
        game("cite-neon", "Cité Néon", "ADVENTURE", "Explorez une ville futuriste éclairée au néon.", 540, age = "12+"),
        game("course-des-rapides", "Course des Rapides", "RACING", "Descendez les rapides en bateau le plus vite possible.", 410),
        game("orbite-zero", "Orbite Zéro", "PUZZLE", "Placez des satellites sur la bonne orbite.", 120, age = "3+"),
        game("forge-stellaire", "Forge Stellaire", "STRATEGY", "Bâtissez une flotte et conquérez des systèmes.", 700, age = "12+"),
        game("tir-de-precision", "Tir de Précision", "ACTION", "Épreuves de tir chronométrées, précision avant tout.", 260, age = "12+")
    )
}
