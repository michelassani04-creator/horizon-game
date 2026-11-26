package com.horizon.backend

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
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
import java.util.UUID

private val log = LoggerFactory.getLogger("Routes")

/** JDBC est bloquant : on l'exécute hors des threads réseau. */
internal suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

internal suspend inline fun <reified T : Any> ApplicationCall.body(): T =
    try {
        receive<T>()
    } catch (e: Exception) {
        throw badRequest("invalid_body", "Corps de requête invalide.")
    }

internal fun ApplicationCall.clientIp(trustProxy: Boolean): String {
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

internal fun tooMany() = ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Trop de tentatives, réessayez plus tard.")

/** Authentifie un partenaire par sa clé (en-tête X-Partner-Key ou Authorization: Bearer). */
private suspend fun ApplicationCall.partnerAuth(config: AppConfig, store: Store, limiter: RateLimiter): PartnerRow {
    val ip = clientIp(config.trustProxy)
    if (limiter.count("partner-fail:$ip", 900_000) >= 20) throw tooMany()
    val key = request.headers["X-Partner-Key"]
        ?: request.headers["Authorization"]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        ?: ""
    val partner = if (key.length in 20..200) io { store.findPartnerByKey(Security.sha256Hex(key.trim())) } else null
    if (partner == null || !partner.active) {
        limiter.allow("partner-fail:$ip", Int.MAX_VALUE, 900_000)
        throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Clé partenaire invalide ou désactivée.")
    }
    return partner
}

private fun Route.partnerRoutes(config: AppConfig, store: Store, limiter: RateLimiter) {
    route("/partner") {
        get("/games") {
            val p = call.partnerAuth(config, store, limiter)
            call.respond(PartnerGamesResponse(p.name, p.kind, io { store.partnerGameStats(p.id) }))
        }
        post("/codes") {
            val p = call.partnerAuth(config, store, limiter)
            val req = call.body<PartnerCodesRequest>()
            Validation.gameId(req.gameId)
            if (req.count !in 1..500) throw badRequest("invalid_count", "Nombre de codes : de 1 à 500.")
            if (req.expiresInDays != null && req.expiresInDays !in 1..3650) throw badRequest("invalid_expiry", "Durée de validité invalide.")
            val ref = req.reference.trim()
            if (ref.length !in 1..80) throw badRequest("invalid_reference", "Référence du lot : 1 à 80 caractères.")
            if (!limiter.allow("partner-batch:${p.id}", 60, 3_600_000)) throw tooMany()
            if (!io { store.partnerOwnsGame(p.id, req.gameId) }) {
                throw ApiException(HttpStatusCode.Forbidden, "game_not_allowed", "Ce jeu n'est pas autorisé pour ton compte.")
            }
            val codes = List(req.count) { Security.newRedeemCode() }
            val expires = req.expiresInDays?.let { Instant.now().plus(it.toLong(), ChronoUnit.DAYS) }
            val created = io {
                store.createPartnerCodes(p.id, req.gameId, ref, codes.map { Security.sha256Hex(Security.normalizeCode(it)) }, expires)
            }
            if (!created) {
                throw ApiException(HttpStatusCode.Conflict, "duplicate_reference", "Cette référence de lot a déjà été utilisée : les codes n'ont pas été recréés.")
            }
            log.info("Partenaire {} : {} codes pour {} (lot {})", p.name, codes.size, req.gameId, ref)
            call.respond(HttpStatusCode.Created, CreateCodesResponse(req.gameId, codes))
        }
    }
}

fun Application.installRoutes(config: AppConfig, store: Store) {
    val limiter = RateLimiter()
    // Fournisseurs de paiement disponibles. Les vrais (M-Pesa, Orange, Airtel, cartes) s'ajouteront ici.
    val providers: Map<String, PaymentProvider> = listOf<PaymentProvider>(SandboxProvider(config.sandboxWebhookSecret)).associateBy { it.id }
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

        // Portail des éditeurs (page publique : l'API exige une session).
        get("/portal") {
            call.response.headers.append(
                "Content-Security-Policy",
                "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"
            )
            call.response.headers.append("X-Content-Type-Options", "nosniff")
            call.respondText(portalPageHtml, ContentType.Text.Html)
        }

        route("/v1") {
            get("/config") {
                call.respond(
                    ConfigResponse(
                        apiVersion = "1",
                        minAppVersion = "0.3.0",
                        latestAppVersion = "0.3.0",
                        features = mapOf(
                            "payments" to true,
                            "paymentsSandbox" to true,
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

                post("/redeem") {
                    val auth = call.authenticate(store)
                    val req = call.body<RedeemRequest>()
                    if (!limiter.allow("redeem:${auth.user.id}", 10, 900_000)) throw tooMany()
                    val normalized = Security.normalizeCode(req.code)
                    if (normalized.length != 16) throw badRequest("invalid_code", "Code invalide : il contient 16 caractères.")
                    val game = io { store.redeemCode(auth.user.id, Security.sha256Hex(normalized)) }
                        ?: throw ApiException(HttpStatusCode.NotFound, "invalid_code", "Code invalide, expiré ou déjà utilisé.")
                    call.respond(game)
                }

                get("/games") {
                    val auth = call.authenticate(store)
                    val items = io { store.ownedGames(auth.user.id) }
                    call.respond(GameListResponse(items, items.size, 1, items.size))
                }

                // Jeu gratuit (prix = 0) : ajout direct à la bibliothèque.
                post("/games/{id}/claim") {
                    val auth = call.authenticate(store)
                    val gameId = Validation.gameId(call.parameters["id"] ?: "")
                    val game = io { store.getGame(gameId) }
                        ?: throw ApiException(HttpStatusCode.NotFound, "game_not_found", "Jeu introuvable.")
                    if (game.priceCents != 0) throw badRequest("not_free", "Ce jeu n'est pas gratuit.")
                    io { store.grantGame(auth.user.id, gameId, "free") }
                    call.respond(io { store.getGame(gameId, includeUnpublished = true) } ?: game)
                }

                route("/orders") {
                    post {
                        val auth = call.authenticate(store)
                        val req = call.body<CreateOrderRequest>()
                        if (!limiter.allow("order:${auth.user.id}", 20, 3_600_000)) throw tooMany()
                        val provider = providers[req.provider]
                            ?: throw badRequest("invalid_provider", "Moyen de paiement indisponible.")
                        val gameId = Validation.gameId(req.gameId)
                        val game = io { store.getGame(gameId) }
                            ?: throw ApiException(HttpStatusCode.NotFound, "game_not_found", "Jeu introuvable.")
                        val price = game.priceCents
                        if (price == null || price <= 0) throw badRequest("not_for_sale", "Ce jeu n'est pas en vente dans l'application.")
                        if (io { store.ownsGame(auth.user.id, gameId) }) {
                            throw ApiException(HttpStatusCode.Conflict, "already_owned", "Tu possèdes déjà ce jeu.")
                        }
                        val order = io { store.createOrder(auth.user.id, game, provider.id) }
                        call.respond(HttpStatusCode.Created, order.copy(message = provider.initiate(order).message))
                    }
                    get {
                        val auth = call.authenticate(store)
                        call.respond(io { store.listOrders(auth.user.id) })
                    }
                    get("/{id}") {
                        val auth = call.authenticate(store)
                        val id = runCatching { UUID.fromString(call.parameters["id"] ?: "") }.getOrNull()
                            ?: throw ApiException(HttpStatusCode.NotFound, "order_not_found", "Commande introuvable.")
                        call.respond(io { store.getOrder(auth.user.id, id) }
                            ?: throw ApiException(HttpStatusCode.NotFound, "order_not_found", "Commande introuvable."))
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

            // ---------- Notifications des fournisseurs de paiement (authentifiées par signature) ----------
            post("/payments/webhook/{provider}") {
                val provider = providers[call.parameters["provider"] ?: ""]
                    ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Introuvable.")
                val body = call.receiveText()
                val result = provider.verifyWebhook({ call.request.headers[it] }, body)
                    ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_signature", "Signature invalide.")
                io { if (result.paid) store.markOrderPaid(result.orderId) else store.markOrderFailed(result.orderId) }
                call.respond(mapOf("status" to "ok"))
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
                    val result = io { store.listGames(q["query"]?.take(80), category, page, pageSize) }
                    call.respond(result.copy(items = result.items.map { it.toPublic() }))
                }
                get("/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val game = io { store.getGame(id) }
                        ?: throw ApiException(HttpStatusCode.NotFound, "game_not_found", "Jeu introuvable.")
                    call.respond(game.toPublic())
                }
            }

            // ---------- Publication de jeux (administrateur) ----------
            adminRoutes(config, store, limiter, providers)
            partnerRoutes(config, store, limiter)
            publisherRoutes(config, store, limiter)
            adminPublisherRoutes(config, store, limiter)
        }
    }
}

private val portalPageHtml: String by lazy {
    object {}.javaClass.getResourceAsStream("/portal/index.html")?.bufferedReader()?.readText()
        ?: "<h1>Portail introuvable</h1>"
}

private val adminPageHtml: String by lazy {
    object {}.javaClass.getResourceAsStream("/admin/index.html")?.bufferedReader()?.readText()
        ?: "<h1>Page d'administration introuvable</h1>"
}

private fun Route.adminRoutes(config: AppConfig, store: Store, limiter: RateLimiter, providers: Map<String, PaymentProvider>) {
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

    get("/admin/orders") {
        call.requireAdmin()
        call.respond(AdminOrdersResponse(io { store.adminListOrders() }))
    }

    // Simule la notification d'un fournisseur de TEST : réservé aux commandes « sandbox ».
    post("/admin/orders/{id}/sandbox-confirm") {
        call.requireAdmin()
        val order = io { runCatching { UUID.fromString(call.parameters["id"] ?: "") }.getOrNull()?.let { store.getOrderAny(it) } }
            ?: throw ApiException(HttpStatusCode.NotFound, "order_not_found", "Commande introuvable.")
        if (providers[order.provider]?.sandbox != true) throw badRequest("not_sandbox", "Seules les commandes de test peuvent être simulées.")
        val ok = io { store.markOrderPaid(UUID.fromString(order.id)) }
        if (!ok) throw ApiException(HttpStatusCode.Conflict, "not_pending", "Cette commande n'est plus en attente.")
        log.info("Commande de test {} confirmée", order.id)
        call.respond(mapOf("status" to "paid"))
    }

    post("/admin/orders/{id}/sandbox-fail") {
        call.requireAdmin()
        val order = io { runCatching { UUID.fromString(call.parameters["id"] ?: "") }.getOrNull()?.let { store.getOrderAny(it) } }
            ?: throw ApiException(HttpStatusCode.NotFound, "order_not_found", "Commande introuvable.")
        if (providers[order.provider]?.sandbox != true) throw badRequest("not_sandbox", "Seules les commandes de test peuvent être simulées.")
        io { store.markOrderFailed(UUID.fromString(order.id)) }
        call.respond(mapOf("status" to "failed"))
    }

    get("/admin/stats") {
        call.requireAdmin()
        call.respond(io { store.stats() })
    }

    post("/admin/codes") {
        call.requireAdmin()
        val req = call.body<CreateCodesRequest>()
        Validation.gameId(req.gameId)
        if (req.count !in 1..200) throw badRequest("invalid_count", "Nombre de codes : de 1 à 200.")
        if (req.expiresInDays != null && req.expiresInDays !in 1..3650) throw badRequest("invalid_expiry", "Durée de validité invalide.")
        io { store.getGame(req.gameId, includeUnpublished = true) }
            ?: throw ApiException(HttpStatusCode.NotFound, "game_not_found", "Jeu introuvable.")
        val codes = List(req.count) { Security.newRedeemCode() }
        val expires = req.expiresInDays?.let { Instant.now().plus(it.toLong(), ChronoUnit.DAYS) }
        io { store.createCodes(req.gameId, codes.map { Security.sha256Hex(Security.normalizeCode(it)) }, expires) }
        log.info("{} codes générés pour le jeu {}", codes.size, req.gameId)
        call.respond(HttpStatusCode.Created, CreateCodesResponse(req.gameId, codes))
    }

    get("/admin/codes/{gameId}") {
        call.requireAdmin()
        val id = Validation.gameId(call.parameters["gameId"] ?: "")
        call.respond(io { store.codeStats(id) })
    }

    route("/admin/partners") {
        get {
            call.requireAdmin()
            call.respond(PartnersResponse(io { store.listPartners() }))
        }
        post {
            call.requireAdmin()
            val req = call.body<CreatePartnerRequest>()
            val name = req.name.trim()
            if (name.length !in 2..80) throw badRequest("invalid_name", "Nom : 2 à 80 caractères.")
            if (req.kind !in setOf("publisher", "reseller")) throw badRequest("invalid_kind", "Type : publisher ou reseller.")
            req.gameIds.forEach { Validation.gameId(it) }
            if (!io { store.gamesExist(req.gameIds) }) throw badRequest("game_not_found", "Un des jeux indiqués n'existe pas.")
            val key = "hzp_" + Security.newToken()
            val id = io { store.createPartner(name, req.kind, Security.sha256Hex(key), req.gameIds) }
            val dto = io { store.getPartner(id) }!!
            call.respond(HttpStatusCode.Created, PartnerKeyResponse(dto, key))
        }
        patch("/{id}") {
            call.requireAdmin()
            val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                ?: throw badRequest("invalid_id", "Identifiant invalide.")
            val req = call.body<UpdatePartnerRequest>()
            req.gameIds?.let { ids ->
                ids.forEach { Validation.gameId(it) }
                if (!io { store.gamesExist(ids) }) throw badRequest("game_not_found", "Un des jeux indiqués n'existe pas.")
            }
            if (!io { store.updatePartner(id, req.active, req.gameIds) }) {
                throw ApiException(HttpStatusCode.NotFound, "partner_not_found", "Partenaire introuvable.")
            }
            call.respond(io { store.getPartner(id) }!!)
        }
        post("/{id}/rotate-key") {
            call.requireAdmin()
            val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                ?: throw badRequest("invalid_id", "Identifiant invalide.")
            val key = "hzp_" + Security.newToken()
            if (!io { store.rotatePartnerKey(id, Security.sha256Hex(key)) }) {
                throw ApiException(HttpStatusCode.NotFound, "partner_not_found", "Partenaire introuvable.")
            }
            call.respond(PartnerKeyResponse(io { store.getPartner(id) }!!, key))
        }
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
