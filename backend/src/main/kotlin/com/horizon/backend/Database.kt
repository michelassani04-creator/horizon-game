package com.horizon.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import javax.sql.DataSource

object Database {
    private val log = LoggerFactory.getLogger("Database")

    /** Se connecte à PostgreSQL ; réessaie pendant ~1 minute le temps que la base démarre. */
    fun connect(config: AppConfig): HikariDataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = 5
            minimumIdle = 0
            // Les bases gratuites se mettent en veille : on renouvelle les connexions avant leur coupure.
            idleTimeout = 60_000
            maxLifetime = 240_000
            connectionTimeout = 20_000
        }
        for (attempt in 1..30) {
            try {
                return HikariDataSource(hikari)
            } catch (e: Exception) {
                if (attempt == 30) throw e
                log.warn("Base de données indisponible (essai $attempt/30), nouvelle tentative dans 2 s")
                Thread.sleep(2_000)
            }
        }
        error("Connexion à la base impossible")
    }

    fun migrate(ds: DataSource) {
        val sql = Database::class.java.getResourceAsStream("/schema.sql")
            ?.bufferedReader()?.readText()
            ?: error("schema.sql introuvable")
        ds.connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
        log.info("Schéma de base de données à jour")
    }
}
