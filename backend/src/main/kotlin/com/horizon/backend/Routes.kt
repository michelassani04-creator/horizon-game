package com.horizon.backend

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = LoggerFactory.getLogger("Routes")

/** JDBC est bloquant : on l'exécute hors des threads réseau. */
private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

private suspend inline fun <reified T : Any> ApplicationCall.body(): T =
    try {
        receive<T>()
    } catch (e: Exception) {
        throw badRequest("invalid_body", "Corps de requête invalide.")
    }

private fun ApplicationCall.clientIp(trustProxy: Boolean): String {
    if (trustProxy) {
        request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return request.origin.remoteHost
}

private suspend fun ApplicationCall.authenticate(store: Store): AuthContext {
    val header = request.headers["Authorization"] ?: throw unauthorized()
    if (!header.startsWith("Bearer ")) throw unauthorized()
    val token = header.removePrefix("Bearer ").trim()
    if (token.isEmpty()) throw unauthorized()
    return io { store.findSession(Security.sha256Hex(token)) } ?: throw unauthorized("Session expirée, reconnectez-vous.")
}

private fun tooMany() = ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Trop de tentatives, réessayez plus tard.")

fun Application.installRoutes(config: AppConfig, store: Store) {
    val limiter = RateLimiter()
    // Hachage factice : sert à garder un temps de réponse constant quand le compte n'existe pas.
    val dummyHash = Security.hashPassword("horizon-dummy-password")

    fun issueSession(user: UserRow, deviceLabel: String?): AuthResponse {
        val token = Security.newToken()
        val expires = Instant.now().plus(config.sessionDays, ChronoUnit.DAYS)
        store.createSession(user.id, Security.sha256Hex(token), deviceLabel, expires)
        return AuthResponse(token, expires.toString(), user.toDto())
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        // Page d'administration (publique : elle ne contient aucun secret, l'API exige le jeton).
        get("/admin") {
            call.response.headers.append(
                "Content-Security-Policy",
                "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; frame-ancestors 'none'"
            )
            call.response.headers.append("X-Content-Type-Options", "nosniff")
            call.respondText(adminPageHtml, ContentType.Text.Html)
        }

        route("/v1") {
            get("/config") {
                call.respond(
                    ConfigResponse(
                        apiVersion = "1",
                        minAppVersion = "0.3.0",
                        latestAppVersion = "0.3.0",
                        features = mapOf(
                            "phoneVerification" to false,
                            "emailVerification" to true,
                            "store" to true,
                            "gamePass" to false,
                            "socialHub" to false,
                            "cloud" to false
                        ),
                        serverTime = Instant.now().toString()
                    )
                )
            }

            // ---------- HORIZON ID ----------
            route("/auth") {
                post("/register") {
                    val ip = call.clientIp(config.trustProxy)
                    if (!limiter.allow("register:$ip", 10, 3_600_000)) throw tooMany()
                    val req = call.body<RegisterRequest>()
                    val email = req.email?.takeIf { it.isNotBlank() }?.let { Validation.email(it) }
                    val phone = req.phone?.takeIf { it.isNotBlank() }?.let { Validation.phone(it) }
                    if (email == null && phone == null) {
                        throw badRequest("contact_required", "Indiquez une adresse e-mail ou un numéro de téléphone.")
                    }
                    Validation.password(req.password)
                    val name = Validation.displayName(req.displayName)
                    val response = io {
                        val hash = Security.hashPassword(req.password)
                        val user = try {
                            store.createUser(email, phone, name, hash)
                        } catch (e: SQLException) {
                            if (Store.isUniqueViolation(e)) {
                                throw ApiException(HttpStatusCode.Conflict, "account_exists", "Un compte existe déjà avec cet e-mail ou ce numéro.")
                            }
                            throw e
                        }
                        issueSession(user, req.deviceLabel)
                    }
                    call.respond(HttpStatusCode.Created, response)
                }

                post("/login") {
                    val ip = call.clientIp(config.trustProxy)
                    val req = call.body<LoginRequest>()
                    val ident = req.identifier.trim().lowercase()
                    if (!limiter.allow("login-ip:$ip", 30, 900_000) || !limiter.allow("login-id:$ident", 10, 900_000)) throw tooMany()
                    val response = io {
                        val user = if ("@" in ident) store.findUserByEmail(ident)
                        else runCatching { Validation.phone(ident) }.getOrNull()?.let { store.findUserByPhone(it) }
                        val ok = Security.verifyPassword(req.password, user?.passwordHash ?: dummyHash)
                        if (user == null || !ok || user.status != "active") {
                            throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Identifiant ou mot de passe incorrect.")
                        }
                        issueSession(user, req.deviceLabel)
                    }
                    call.respond(response)
                }

                post("/logout") {
                    val auth = call.authenticate(store)
                    io { store.revokeSession(auth.sessionId) }
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            // ---------- Profil ----------
            route("/me") {
                get {
                    val auth = call.authenticate(store)
                    call.respond(auth.user.toDto())
                }

                patch {
                    val auth = call.authenticate(store)
                    val req = call.body<UpdateMeRequest>()
                    val clean = req.copy(
                        displayName = req.displayName?.let { Validation.displayName(it) },
                        avatarUrl = req.avatarUrl?.also {
                            if (it.length > 500 || !it.startsWith("https://")) {
                                throw badRequest("invalid_avatar", "L'avatar doit être une URL https:// de 500 caractères maximum.")
                            }
                        }
                    )
                    clean.language?.let { if (it !in Validation.languages) throw badRequest("invalid_language", "Langue non prise en charge.") }
                    clean.theme?.let { if (it !in Validation.themes) throw badRequest("invalid_theme", "Thème inconnu.") }
                    call.respond(io { store.updateUser(auth.user.id, clean) }.toDto())
                }

                delete {
                    val auth = call.authenticate(store)
                    val req = call.body<DeleteAccountRequest>()
                    if (!limiter.allow("delete:${auth.user.id}", 5, 900_000)) throw tooMany()
                    val ok = io { Security.verifyPassword(req.password, auth.user.passwordHash) }
                    if (!ok) throw ApiException(HttpStatusCode.Forbidden, "invalid_password", "Mot de passe incorrect.")
                    io { store.deleteUser(auth.user.id) }
                    call.respond(HttpStatusCode.NoContent)
                }

                route("/verify") {
                    post("/request") {
                        val auth = call.authenticate(store)
                        val req = call.body<VerifyRequest>()
                        if (req.channel == "phone") {
                            throw ApiException(HttpStatusCode.NotImplemented, "phone_verification_unavailable", "La vérification par SMS n'est pas encore disponible.")
                        }
                        if (req.channel != "email" || auth.user.email == null) {
                            throw badRequest("invalid_channel", "Aucune adresse e-mail à vérifier sur ce compte.")
                        }
                        if (auth.user.emailVerified) throw badRequest("already_verified", "Adresse e-mail déjà vérifiée.")
                        if (!limiter.allow("verify-req:${auth.user.id}", 5, 3_600_000)) throw tooMany()
                        val code = Security.newCode()
                        io {
                            store.createCode(auth.user.id, "email", Security.sha256Hex(code), Instant.now().plus(15, ChronoUnit.MINUTES))
                        }
                        if (config.logVerificationCodes) {
                            log.info("Code de vérification e-mail pour {} : {}", auth.user.email, code)
                        }
                        call.respond(HttpStatusCode.Accepted, mapOf("status" to "sent"))
                    }

                    post("/confirm") {
                        val auth = call.authenticate(store)
                        val req = call.body<VerifyConfirmRequest>()
                        if (req.channel != "email") throw badRequest("invalid_channel", "Canal inconnu.")
                        if (!limiter.allow("verify-conf:${auth.user.id}", 20, 3_600_000)) throw tooMany()
                        val row = io { store.activeCode(auth.user.id, "email") }
                            ?: throw badRequest("code_expired", "Code expiré ou inexistant : demandez-en un nouveau.")
                        if (row.attempts >= 5) throw badRequest("code_locked", "Trop d'essais : demandez un nouveau code.")
                        io { store.bumpCodeAttempts(row.id) }
                        if (!Security.constantTimeEquals(row.codeHash, Security.sha256Hex(req.code.trim()))) {
                            throw badRequest("code_invalid", "Code incorrect.")
                        }
                        io {
                            store.markCodeUsed(row.id)
                            store.markVerified(auth.user.id, "email")
                        }
                        call.respond(io { store.findUserById(auth.user.id) }?.toDto() ?: throw unauthorized())
                    }
                }

                route("/favorites") {
                    get {
                        val auth = call.authenticate(store)
                        call.respond(FavoritesResponse(io { store.favorites(auth.user.id) }))
                    }
                    put {
                        val auth = call.authenticate(store)
                        val req = call.body<SyncFavoritesRequest>()
                        call.respond(FavoritesResponse(io { store.replaceFavorites(auth.user.id, req.gameIds) }))
                    }
                    put("/{gameId}") {
                        val auth = call.authenticate(store)
                        val gameId = Validation.gameId(call.parameters["gameId"] ?: "")
                        val exists = io { store.addFavorite(auth.user.id, gameId) }
                        if (!exists) throw ApiException(HttpStatusCode.NotFound, "game_not_found", "Jeu introuvable.")
                        call.respond(FavoritesResponse(io { store.favorites(auth.user.id) }))
                    }
                    delete("/{gameId}") {
                        val auth = call.authenticate(store)
                        val gameId = Validation.gameId(call.parameters["gameId"] ?: "")
                        io { store.removeFavorite(auth.user.id, gameId) }
                        call.respond(FavoritesResponse(io { store.favorites(auth.user.id) }))
                    }
                }
            }

            // ---------- HORIZON Store (public) ----------
            route("/games") {
                get {
                    val q = call.request.queryParameters
                    val category = q["category"]?.uppercase()?.takeIf { it.isNotBlank() }
                    if (category != null && category !in Validation.categories) {
                        throw badRequest("invalid_category", "Catégorie inconnue.")
                    }
                    val page = (q["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val pageSize = (q["pageSize"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
                    call.respond(io { store.listGames(q["query"]?.take(80), category, page, pageSize) })
                }
                get("/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val game = io { store.getGame(id) }
                        ?: throw ApiException(HttpStatusCode.NotFound, "game_not_found", "Jeu introuvable.")
                    call.respond(game)
                }
            }

            // ---------- Publication de jeux (administrateur) ----------
            adminRoutes(config, store, limiter)
        }
    }
}

private val adminPageHtml: String by lazy {
    object {}.javaClass.getResourceAsStream("/admin/index.html")?.bufferedReader()?.readText()
        ?: "<h1>Page d'administration introuvable</h1>"
}

private fun Route.adminRoutes(config: AppConfig, store: Store, limiter: RateLimiter) {
    fun ApplicationCall.requireAdmin() {
        val expected = config.adminToken
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Introuvable.")
        val ip = clientIp(config.trustProxy)
        // Après 10 échecs en 15 minutes, même le bon jeton est refusé (anti-devinette).
        if (limiter.count("admin-fail:$ip", 900_000) >= 10) throw tooMany()
        val given = request.headers["X-Admin-Token"] ?: ""
        if (!Security.constantTimeEquals(expected, given)) {
            limiter.allow("admin-fail:$ip", Int.MAX_VALUE, 900_000)
            throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Jeton administrateur invalide.")
        }
    }

    get("/admin/stats") {
        call.requireAdmin()
        call.respond(io { store.stats() })
    }

    route("/admin/games") {
        get {
            call.requireAdmin()
            call.respond(io { store.listAllGames() })
        }
        put("/{id}") {
            call.requireAdmin()
            val id = Validation.gameId(call.parameters["id"] ?: "")
            val game = call.body<GameDto>()
            if (game.id != id) throw badRequest("id_mismatch", "L'identifiant du corps doit correspondre à l'URL.")
            val clean = Validation.game(game)
            io { store.upsertGame(clean) }
            call.respond(clean)
        }
        delete("/{id}") {
            call.requireAdmin()
            val id = Validation.gameId(call.parameters["id"] ?: "")
            val removed = io { store.deleteGame(id) }
            if (!removed) throw ApiException(HttpStatusCode.NotFound, "game_not_found", "Jeu introuvable.")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
