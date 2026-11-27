package com.horizon.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityTest {
    @Test
    fun passwordRoundTrip() {
        val hash = Security.hashPassword("mot-de-passe-solide")
        assertTrue(Security.verifyPassword("mot-de-passe-solide", hash))
        assertFalse(Security.verifyPassword("autre-mot-de-passe", hash))
        assertFalse(Security.verifyPassword("x", "format-invalide"))
    }

    @Test
    fun tokensAreUniqueAndHashed() {
        val a = Security.newToken()
        val b = Security.newToken()
        assertTrue(a != b)
        assertEquals(64, Security.sha256Hex(a).length)
    }

    @Test
    fun codeHasSixDigits() {
        repeat(50) { assertTrue(Regex("^[0-9]{6}$").matches(Security.newCode())) }
    }

    @Test
    fun rateLimiterBlocksAfterMax() {
        val limiter = RateLimiter()
        repeat(3) { assertTrue(limiter.allow("k", 3, 60_000)) }
        assertFalse(limiter.allow("k", 3, 60_000))
        assertTrue(limiter.allow("autre", 3, 60_000))
    }

    @Test
    fun validation() {
        assertEquals("a@b.cd", Validation.email("  A@B.cd "))
        assertEquals("+243812345678", Validation.phone("+243 81 234 5678"))
        assertFailsWith<ApiException> { Validation.email("pas-un-email") }
        assertFailsWith<ApiException> { Validation.phone("0812345678") }
        assertFailsWith<ApiException> { Validation.password("court") }
        assertFailsWith<ApiException> { Validation.displayName("x") }
    }

    @Test
    fun redeemCodes() {
        val c = Security.newRedeemCode()
        assertTrue(Regex("^[A-Z2-9]{4}(-[A-Z2-9]{4}){3}$").matches(c))
        assertEquals(16, Security.normalizeCode(c).length)
        assertEquals(Security.normalizeCode(c), Security.normalizeCode(c.lowercase().replace("-", " ")))
    }

    @Test
    fun sandboxWebhookSignature() {
        val secret = "0123456789012345678901234567890123"
        val provider = SandboxProvider(secret)
        val id = java.util.UUID.randomUUID()
        val body = """{"orderId":"$id","status":"paid"}"""
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val sig = mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
        val ok = provider.verifyWebhook({ if (it == "X-Horizon-Signature") sig else null }, body)
        assertEquals(id, ok?.orderId)
        assertTrue(ok?.paid == true)
        assertEquals(null, provider.verifyWebhook({ "mauvaise-signature" }, body))
        assertEquals(null, SandboxProvider(null).verifyWebhook({ sig }, body))
    }

    @Test
    fun oldPasswordHashesStillWorkAndAreFlaggedForRehash() {
        // Hachage au format historique (120 000 itérations) : toujours accepté, mais à re-hacher.
        val salt = ByteArray(16) { it.toByte() }
        val spec = javax.crypto.spec.PBEKeySpec("ancien-mot-de-passe".toCharArray(), salt, 120_000, 256)
        val hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val b64 = java.util.Base64.getEncoder()
        val old = "pbkdf2\$120000\$${b64.encodeToString(salt)}\$${b64.encodeToString(hash)}"
        assertTrue(Security.verifyPassword("ancien-mot-de-passe", old))
        assertTrue(Security.needsRehash(old))
        assertFalse(Security.needsRehash(Security.hashPassword("nouveau-mot-de-passe")))
        assertFalse(Security.needsRehash("format-invalide"))
    }

    @Test
    fun absurdIterationCountIsRejected() {
        assertFalse(Security.verifyPassword("x", "pbkdf2\$2000000000\$AAAA\$AAAA"))
    }

    @Test
    fun rateLimiterMemoryIsBounded() {
        val limiter = RateLimiter()
        repeat(120_000) { limiter.allow("login-id:inconnu-$it", 10, 900_000) }
        assertTrue(limiter.size() <= 50_000)
        // Une clé démesurée est tronquée : elle ne peut pas servir à gonfler la mémoire.
        val long = "x".repeat(10_000)
        repeat(3) { assertTrue(limiter.allow(long, 3, 60_000)) }
        assertFalse(limiter.allow(long, 3, 60_000))
    }

    @Test
    fun singleLineTextRejectsControlCharacters() {
        assertEquals("Studio Kasai", Validation.line("  Studio Kasai ", 2, 80, "c", "Nom"))
        assertFailsWith<ApiException> { Validation.line("Studio\nClause: 0 %", 2, 80, "c", "Nom") }
        assertFailsWith<ApiException> { Validation.line("Studio\r\nFAUX LOG", 2, 80, "c", "Nom") }
        assertFailsWith<ApiException> { Validation.line("Studio\u2028X", 2, 80, "c", "Nom") }
        assertFailsWith<ApiException> { Validation.line("x", 2, 80, "c", "Nom") }
        assertFailsWith<ApiException> { Validation.displayName("Jean\nAdmin") }
    }
}
