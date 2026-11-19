package com.horizon.backend

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Security {
    private val random = SecureRandom()
    private const val ITERATIONS = 120_000
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
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
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

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

/** Limiteur de débit en mémoire (fenêtre glissante) : freine le brute-force sur un seul serveur. */
class RateLimiter {
    private val hits = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun allow(key: String, max: Int, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val queue = hits.getOrPut(key) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() > windowMs) queue.removeFirst()
        if (hits.size > 50_000) hits.entries.removeIf { it.value.isEmpty() }
        if (queue.size >= max) return false
        queue.addLast(now)
        return true
    }
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
        if (g.title.trim().length !in 1..80) throw badRequest("invalid_title", "Titre : 1 à 80 caractères.")
        if (g.publisher.trim().length !in 1..80) throw badRequest("invalid_publisher", "Éditeur : 1 à 80 caractères.")
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
        return g.copy(
            title = g.title.trim(),
            publisher = g.publisher.trim(),
            apkSha256 = g.apkSha256?.lowercase()
        )
    }
}
