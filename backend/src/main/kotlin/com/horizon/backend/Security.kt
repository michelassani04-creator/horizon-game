package com.horizon.backend

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Security {
    private val random = SecureRandom()
    /** 600 000 : valeur recommandée par OWASP pour PBKDF2-HMAC-SHA256. Les anciens hachages (120 000) restent valides. */
    private const val ITERATIONS = 600_000
    private const val MAX_STORED_ITERATIONS = 5_000_000
    private const val KEY_BITS = 256

    /** Format : pbkdf2$itérations$sel$hash (Base64). */
    fun hashPassword(password: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val hash = pbkdf2(password, salt, ITERATIONS)
        val b64 = Base64.getEncoder()
        return "pbkdf2$$ITERATIONS$${b64.encodeToString(salt)}$${b64.encodeToString(hash)}"
    }

    fun verifyPassword(password: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_STORED_ITERATIONS } ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    /** true si le hachage stocké utilise moins d'itérations que la valeur actuelle : à re-hacher après une connexion réussie. */
    fun needsRehash(stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        return iterations < ITERATIONS
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Jeton opaque de 256 bits ; seul son hachage SHA-256 est stocké en base. */
    fun newToken(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun newCode(): String = "%06d".format(random.nextInt(1_000_000))

    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Code de téléchargement lisible : XXXX-XXXX-XXXX-XXXX (80 bits, sans caractères ambigus). */
    fun newRedeemCode(): String =
        (0 until 16).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
            .chunked(4).joinToString("-") { it.joinToString("") }

    /** Majuscules, sans tirets ni espaces. */
    fun normalizeCode(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

/**
 * Limiteur de débit en mémoire (fenêtre glissante) : freine le brute-force sur un seul serveur.
 * La mémoire est bornée : clés tronquées, entrées inactives purgées, et au plus [MAX_KEYS] clés
 * (les moins récemment utilisées sont évincées), même si un attaquant invente des clés à volonté.
 * Toutes les fenêtres utilisées par l'application sont de 1 heure au plus.
 */
class RateLimiter {
    private companion object {
        const val MAX_KEY_LENGTH = 200
        const val MAX_KEYS = 50_000
        const val STALE_AFTER_MS = 3_600_000L
        const val PURGE_EVERY = 1_000
    }

    private val hits = object : LinkedHashMap<String, ArrayDeque<Long>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<Long>>?): Boolean = size > MAX_KEYS
    }
    private var calls = 0

    private fun norm(key: String) = if (key.length > MAX_KEY_LENGTH) key.substring(0, MAX_KEY_LENGTH) else key

    private fun purgeIfDue(now: Long) {
        if (++calls < PURGE_EVERY) return
        calls = 0
        hits.entries.removeIf { it.value.isEmpty() || now - it.value.last() > STALE_AFTER_MS }
    }

    /** Nombre d'événements enregistrés dans la fenêtre, sans en ajouter. */
    @Synchronized
    fun count(key: String, windowMs: Long): Int {
        val now = System.currentTimeMillis()
        val queue = hits[norm(key)] ?: return 0
        while (queue.isNotEmpty() && now - queue.first() > windowMs) queue.removeFirst()
        return queue.size
    }

    @Synchronized
    fun allow(key: String, max: Int, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        purgeIfDue(now)
        val queue = hits.getOrPut(norm(key)) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() > windowMs) queue.removeFirst()
        if (queue.size >= max) return false
        queue.addLast(now)
        return true
    }

    /** Nombre de clés suivies (pour les tests). */
    @Synchronized
    fun size(): Int = hits.size
}

object Validation {
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$")
    private val phoneRegex = Regex("^\\+[1-9][0-9]{7,14}$")
    private val gameIdRegex = Regex("^[a-z0-9][a-z0-9-]{1,59}$")
    private val sha256Regex = Regex("^[0-9a-fA-F]{64}$")

    val categories = setOf("ACTION", "RACING", "PUZZLE", "ADVENTURE", "STRATEGY")
    val compatibilities = setOf("VERIFIED", "DECLARED", "UNKNOWN")
    val languages = setOf("fr", "en")
    val themes = setOf("dark", "light")

    fun email(raw: String): String {
        val v = raw.trim().lowercase()
        if (!emailRegex.matches(v)) throw badRequest("invalid_email", "Adresse e-mail invalide.")
        return v
    }

    /** Numéro au format international : +243..., espaces et tirets tolérés. */
    fun phone(raw: String): String {
        val v = raw.trim().replace(Regex("[\\s().-]"), "")
        if (!phoneRegex.matches(v)) {
            throw badRequest("invalid_phone", "Numéro invalide : utilisez le format international, par ex. +243812345678.")
        }
        return v
    }

    fun password(raw: String) {
        if (raw.length < 8) throw badRequest("weak_password", "Le mot de passe doit contenir au moins 8 caractères.")
        if (raw.length > 128) throw badRequest("invalid_password", "Mot de passe trop long (128 caractères maximum).")
    }

    fun displayName(raw: String): String {
        val v = raw.trim()
        if (v.length !in 2..32) throw badRequest("invalid_name", "Le pseudo doit contenir de 2 à 32 caractères.")
        if (hasControlChars(v)) throw badRequest("invalid_name", "Le pseudo ne peut pas contenir de retour à la ligne ni de caractère de contrôle.")
        return v
    }

    /** Retours à la ligne, tabulations et autres caractères de contrôle (y compris U+2028/U+2029). */
    fun hasControlChars(v: String): Boolean = v.any { it.isISOControl() || it == '\u2028' || it == '\u2029' }

    /** Texte d'une seule ligne (nom, pays…) : longueur bornée, aucun caractère de contrôle. */
    fun line(raw: String, min: Int, max: Int, code: String, label: String): String {
        val v = raw.trim()
        if (v.length !in min..max) throw badRequest(code, "$label : $min à $max caractères.")
        if (hasControlChars(v)) throw badRequest(code, "$label : pas de retour à la ligne ni de caractère de contrôle.")
        return v
    }

    fun gameId(raw: String): String {
        if (!gameIdRegex.matches(raw)) {
            throw badRequest("invalid_game_id", "Identifiant de jeu invalide (minuscules, chiffres et tirets).")
        }
        return raw
    }

    fun game(g: GameDto): GameDto {
        gameId(g.id)
        if (g.title.trim().length !in 1..80 || hasControlChars(g.title.trim())) throw badRequest("invalid_title", "Titre : 1 à 80 caractères, sur une seule ligne.")
        if (g.publisher.trim().length !in 1..80 || hasControlChars(g.publisher.trim())) throw badRequest("invalid_publisher", "Éditeur : 1 à 80 caractères, sur une seule ligne.")
        if (g.ageRating.length > 32) throw badRequest("invalid_age_rating", "Classification d'âge : 32 caractères maximum.")
        if ((g.packageName?.length ?: 0) > 200) throw badRequest("invalid_package_name", "Nom de paquet : 200 caractères maximum.")
        if ((g.apkUrl?.length ?: 0) > 2000 || (g.packUrl?.length ?: 0) > 2000) throw badRequest("invalid_url", "Adresse trop longue (2000 caractères maximum).")
        if (g.category !in categories) throw badRequest("invalid_category", "Catégorie inconnue.")
        if (g.compatibility !in compatibilities) throw badRequest("invalid_compatibility", "Compatibilité inconnue.")
        if (g.description.length > 4000) throw badRequest("invalid_description", "Description trop longue.")
        if (g.sizeMb !in 0..100_000) throw badRequest("invalid_size", "Taille invalide.")
        g.apkUrl?.let {
            if (!it.startsWith("https://")) throw badRequest("invalid_apk_url", "L'URL de l'APK doit commencer par https://.")
        }
        g.apkSha256?.let {
            if (!sha256Regex.matches(it)) throw badRequest("invalid_sha256", "Empreinte SHA-256 invalide (64 caractères hexadécimaux).")
        }
        g.packUrl?.let {
            if (!it.startsWith("https://")) throw badRequest("invalid_pack_url", "L'adresse du paquet doit commencer par https://.")
        }
        g.packSha256?.let {
            if (!sha256Regex.matches(it)) throw badRequest("invalid_pack_sha256", "Empreinte SHA-256 du paquet invalide (64 caractères hexadécimaux).")
        }
        if (g.packUrl != null && g.packSha256 == null) {
            throw badRequest("pack_sha256_required", "L'empreinte SHA-256 est obligatoire avec une adresse de paquet.")
        }
        g.priceCents?.let {
            if (it !in 0..10_000_000) throw badRequest("invalid_price", "Prix invalide.")
        }
        if (g.currency !in setOf("USD", "EUR", "CDF")) throw badRequest("invalid_currency", "Devise non prise en charge (USD, EUR ou CDF).")
        if (g.versionCode < 1) throw badRequest("invalid_version", "Le numéro de version doit être au moins 1.")
        if (g.versionName.trim().length !in 1..32 || hasControlChars(g.versionName.trim())) throw badRequest("invalid_version_name", "Nom de version : 1 à 32 caractères, sur une seule ligne.")
        if ((g.engine?.length ?: 0) > 32 || (g.engineVersion?.length ?: 0) > 32) {
            throw badRequest("invalid_engine", "Moteur : 32 caractères maximum.")
        }
        return g.copy(
            title = g.title.trim(),
            publisher = g.publisher.trim(),
            apkSha256 = g.apkSha256?.lowercase(),
            packSha256 = g.packSha256?.lowercase(),
            versionName = g.versionName.trim(),
            engine = g.engine?.trim()?.takeIf { it.isNotEmpty() },
            engineVersion = g.engineVersion?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}
