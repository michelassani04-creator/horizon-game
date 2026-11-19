package com.horizon.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigTest {
    @Test
    fun convertsNeonStyleUrl() {
        val r = jdbcFromDatabaseUrl("postgresql://neondb_owner:p%40ss@ep-abc.eu-central-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require")!!
        assertEquals("jdbc:postgresql://ep-abc.eu-central-1.aws.neon.tech:5432/neondb?sslmode=require&prepareThreshold=0", r.first)
        assertEquals("neondb_owner", r.second)
        assertEquals("p@ss", r.third)
    }

    @Test
    fun rejectsOtherSchemes() {
        assertNull(jdbcFromDatabaseUrl("mysql://u:p@h/db"))
    }

    @Test
    fun databaseUrlFeedsConfig() {
        val cfg = AppConfig.fromEnv(mapOf("DATABASE_URL" to "postgres://u:pw@db.example.com:6543/horizon"))
        assertEquals("u", cfg.dbUser)
        assertEquals("pw", cfg.dbPassword)
        assertEquals("jdbc:postgresql://db.example.com:6543/horizon?sslmode=require&prepareThreshold=0", cfg.dbUrl)
    }
}
