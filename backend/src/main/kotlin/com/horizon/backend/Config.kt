package com.horizon.backend

/** Configuration lue dans les variables d'environnement : aucun secret dans le code. */
data class AppConfig(
    val port: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    /** Jeton des routes /v1/admin ; absent ou trop court : l'administration est désactivée. */
    val adminToken: String?,
    val sessionDays: Long,
    val seedDemo: Boolean,
    /** true derrière un reverse proxy (Caddy) : l'adresse du client vient de X-Forwarded-For. */
    val trustProxy: Boolean,
    /** Test seulement (LOG_CODES=true) : les codes de vérification e-mail sont écrits dans les logs du serveur. */
    val logVerificationCodes: Boolean,
    /** Secret des notifications signées du fournisseur de test ; absent ou court : notifications désactivées. */
    val sandboxWebhookSecret: String? = null
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            // DATABASE_URL (format postgresql://utilisateur:motdepasse@hote/base, fourni par Neon, Render, etc.)
            // est converti en adresse JDBC, sauf si DB_URL est défini explicitement.
            val fromUrl = if (env["DB_URL"] == null) env["DATABASE_URL"]?.let { jdbcFromDatabaseUrl(it) } else null
            return build(env, fromUrl)
        }

        private fun build(env: Map<String, String>, fromUrl: Triple<String, String, String>?): AppConfig = AppConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            dbUrl = env["DB_URL"] ?: fromUrl?.first ?: "jdbc:postgresql://localhost:5432/horizon",
            dbUser = env["DB_USER"] ?: fromUrl?.second ?: "horizon",
            dbPassword = env["DB_PASSWORD"] ?: fromUrl?.third ?: "",
            adminToken = env["ADMIN_TOKEN"]?.takeIf { it.length >= 24 },
            sessionDays = env["SESSION_DAYS"]?.toLongOrNull()?.coerceIn(1, 365) ?: 30,
            seedDemo = env["SEED_DEMO"]?.lowercase() == "true",
            trustProxy = env["TRUST_PROXY"]?.lowercase() == "true",
            // Désactivé par défaut : un code écrit dans les logs permet à quiconque lit les logs de valider n'importe quelle adresse.
            logVerificationCodes = env["LOG_CODES"]?.lowercase() == "true",
            sandboxWebhookSecret = env["SANDBOX_WEBHOOK_SECRET"]?.takeIf { it.length >= 24 }
        )
    }
}

/**
 * Convertit postgresql://user:pass@hote:port/base?options en (adresse JDBC, utilisateur, mot de passe).
 * Ajoute sslmode=require (bases en ligne) et prepareThreshold=0 (compatible avec les pools de connexions),
 * et retire channel_binding, inconnu du pilote JDBC.
 */
fun jdbcFromDatabaseUrl(raw: String): Triple<String, String, String>? {
    val uri = try {
        java.net.URI(raw.trim())
    } catch (e: Exception) {
        return null
    }
    if (uri.scheme != "postgres" && uri.scheme != "postgresql") return null
    val host = uri.host ?: return null
    val port = if (uri.port == -1) 5432 else uri.port
    val info = uri.rawUserInfo ?: return null
    val user = java.net.URLDecoder.decode(info.substringBefore(':'), "UTF-8")
    val pass = java.net.URLDecoder.decode(info.substringAfter(':', ""), "UTF-8")
    val params = (uri.rawQuery ?: "").split('&')
        .filter { it.isNotBlank() && !it.startsWith("channel_binding") }
        .toMutableList()
    if (params.none { it.startsWith("sslmode") }) params += "sslmode=require"
    if (params.none { it.startsWith("prepareThreshold") }) params += "prepareThreshold=0"
    return Triple("jdbc:postgresql://$host:$port${uri.rawPath}?${params.joinToString("&")}", user, pass)
}
