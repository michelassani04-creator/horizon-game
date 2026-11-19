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
    /** Tant qu'aucun service d'e-mail n'est branché, les codes de vérification sont écrits dans les logs du serveur. */
    val logVerificationCodes: Boolean
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig = AppConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            dbUrl = env["DB_URL"] ?: "jdbc:postgresql://localhost:5432/horizon",
            dbUser = env["DB_USER"] ?: "horizon",
            dbPassword = env["DB_PASSWORD"] ?: "",
            adminToken = env["ADMIN_TOKEN"]?.takeIf { it.length >= 24 },
            sessionDays = env["SESSION_DAYS"]?.toLongOrNull()?.coerceIn(1, 365) ?: 30,
            seedDemo = env["SEED_DEMO"]?.lowercase() == "true",
            trustProxy = env["TRUST_PROXY"]?.lowercase() == "true",
            logVerificationCodes = env["LOG_CODES"]?.lowercase() != "false"
        )
    }
}
