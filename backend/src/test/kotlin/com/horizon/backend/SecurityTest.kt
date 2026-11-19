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
}
