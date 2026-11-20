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
    /** Paquet de jeu (téléchargé dans l'application, visible seulement si le joueur possède le jeu). */
    val packUrl: String? = null,
    val packSha256: String? = null,
    /** Numéro de version croissant : détecte les mises à jour. */
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val engine: String? = null,
    val engineVersion: String? = null,
    /** Prix en centimes : null = pas en vente (code seulement), 0 = gratuit. */
    val priceCents: Int? = null,
    val currency: String = "USD",
    /** Mis en avant dans la bannière du Store. */
    val featured: Boolean = false
)

/** Prix lisible (« 4,99 $ », « Gratuit »), ou null si le jeu n'est pas en vente. */
fun Game.priceLabel(): String? {
    val cents = priceCents ?: return null
    if (cents == 0) return "Gratuit"
    val amount = "%d,%02d".format(cents / 100, cents % 100)
    return when (currency) {
        "USD" -> "$amount $"
        "EUR" -> "$amount €"
        "CDF" -> "$amount FC"
        else -> "$amount $currency"
    }
}
