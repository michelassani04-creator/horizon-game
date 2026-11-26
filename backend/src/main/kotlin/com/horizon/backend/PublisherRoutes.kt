package com.horizon.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val plog = LoggerFactory.getLogger("Publisher")

private suspend fun ApplicationCall.publisherAuth(store: Store): PublisherDto {
    val header = request.headers["Authorization"] ?: throw unauthorized()
    if (!header.startsWith("Bearer ")) throw unauthorized()
    val token = header.removePrefix("Bearer ").trim()
    if (token.isEmpty()) throw unauthorized()
    return io { store.findPublisherBySession(Security.sha256Hex(token)) } ?: throw unauthorized("Session expirée, reconnecte-toi.")
}

private fun PublisherDto.requireActive() {
    if (status != "active") {
        throw ApiException(HttpStatusCode.Forbidden, "not_active", "Ton compte n'est pas encore actif : le contrat doit être signé, puis validé par HORIZON.")
    }
}

private fun notFound(code: String, msg: String) = ApiException(HttpStatusCode.NotFound, code, msg)
private fun conflict(code: String, msg: String) = ApiException(HttpStatusCode.Conflict, code, msg)

private fun ApplicationCall.pathUuid(name: String): UUID =
    runCatching { UUID.fromString(parameters[name]) }.getOrNull() ?: throw badRequest("invalid_id", "Identifiant invalide.")

private fun salesFor(store: Store, p: PublisherDto): SalesResponse {
    val id = UUID.fromString(p.id)
    val lines = store.salesLines(id)
    val payouts = store.payoutsOf(id)
    val bp = Math.round((p.commissionPercent ?: 0.0) * 100).toInt()
    return SalesResponse(lines, Revenue.balances(lines, payouts, bp), payouts)
}

/** Portail des éditeurs : compte, contrat, jeux, codes, ventes. */
fun Route.publisherRoutes(config: AppConfig, store: Store, limiter: RateLimiter) {
    fun issue(p: PublisherDto): PublisherAuthResponse {
        val token = Security.newToken()
        val expires = Instant.now().plus(config.sessionDays, ChronoUnit.DAYS)
        store.createPublisherSession(UUID.fromString(p.id), Security.sha256Hex(token), expires)
        return PublisherAuthResponse(token, expires.toString(), p)
    }

    route("/publisher") {
        post("/register") {
            val ip = call.clientIp(config.trustProxy)
            if (!limiter.allow("pub-register:$ip", 5, 3_600_000)) throw tooMany()
            val req = call.body<PublisherRegisterRequest>()
            val email = Validation.email(req.email)
            Validation.password(req.password)
            val company = req.companyName.trim()
            val contact = req.contactName.trim()
            val country = req.country.trim()
            if (company.length !in 2..80) throw badRequest("invalid_company", "Nom du studio : 2 à 80 caractères.")
            if (contact.length !in 2..80) throw badRequest("invalid_contact", "Nom du responsable : 2 à 80 caractères.")
            if (country.length !in 2..60) throw badRequest("invalid_country", "Pays : 2 à 60 caractères.")
            val phone = req.phone?.takeIf { it.isNotBlank() }?.let { Validation.phone(it) }
            val response = io {
                val hash = Security.hashPassword(req.password)
                val id = try {
                    store.createPublisher(email, hash, company, contact, phone, country)
                } catch (e: SQLException) {
                    if (Store.isUniqueViolation(e)) throw conflict("account_exists", "Un compte éditeur existe déjà avec cet e-mail.")
                    throw e
                }
                issue(store.findPublisher(id)!!)
            }
            plog.info("Nouvel éditeur inscrit : {}", company)
            call.respond(HttpStatusCode.Created, response)
        }

        post("/login") {
            val ip = call.clientIp(config.trustProxy)
            val req = call.body<PublisherLoginRequest>()
            val email = req.email.trim().lowercase()
            if (!limiter.allow("pub-login-ip:$ip", 30, 900_000) || !limiter.allow("pub-login-id:$email", 10, 900_000)) throw tooMany()
            val response = io {
                val found = store.findPublisherLogin(email)
                if (found == null || !Security.verifyPassword(req.password, found.second)) {
                    throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "E-mail ou mot de passe incorrect.")
                }
                issue(found.first)
            }
            call.respond(response)
        }

        post("/logout") {
            val header = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim().orEmpty()
            if (header.isNotEmpty()) io { store.deletePublisherSession(Security.sha256Hex(header)) }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/me") { call.respond(call.publisherAuth(store)) }

        get("/contract") {
            val p = call.publisherAuth(store)
            if (p.status == "registered") {
                throw conflict("contract_not_ready", "Le contrat sera disponible dès que HORIZON aura fixé tes conditions.")
            }
            val signed = p.status != "contract_ready"
            val text = (if (signed) io { store.signedContractText(UUID.fromString(p.id)) } else null) ?: Contract.render(p)
            call.respond(ContractDto(p.contractVersion ?: Contract.VERSION, text, Security.sha256Hex(text), p.commissionPercent ?: 0.0, p.status, p.signedName, p.signedAt))
        }

        post("/contract/sign") {
            val p = call.publisherAuth(store)
            if (p.status != "contract_ready") throw conflict("not_signable", "Le contrat n'est pas à signer pour le moment.")
            val req = call.body<SignContractRequest>()
            val name = req.signedName.trim()
            if (!req.accept) throw badRequest("not_accepted", "Coche « Lu et approuvé » pour signer.")
            if (name.length !in 2..80) throw badRequest("invalid_name", "Saisis ton nom complet (2 à 80 caractères).")
            val text = Contract.render(p)
            val sha = Security.sha256Hex(text)
            if (!Security.constantTimeEquals(req.sha256.lowercase(), sha)) {
                throw conflict("contract_changed", "Le contrat a changé depuis que tu l'as ouvert. Recharge la page et relis-le.")
            }
            val bp = Math.round((p.commissionPercent ?: 0.0) * 100).toInt()
            val ok = io {
                store.signContract(
                    UUID.fromString(p.id), Contract.VERSION, text, sha, bp, name,
                    call.clientIp(config.trustProxy), call.request.headers["User-Agent"]
                )
            }
            if (!ok) throw conflict("not_signable", "Le contrat n'est pas à signer pour le moment.")
            plog.info("Contrat signé par {} ({})", name, p.companyName)
            call.respond(io { store.findPublisher(UUID.fromString(p.id)) }!!)
        }

        get("/games") {
            val p = call.publisherAuth(store)
            p.requireActive()
            call.respond(PublisherGamesResponse(io { store.listPartnerGames(UUID.fromString(p.id)) }))
        }

        put("/games/{id}") {
            val p = call.publisherAuth(store)
            p.requireActive()
            val id = Validation.gameId(call.parameters["id"] ?: "")
            val game = call.body<GameDto>()
            // L'éditeur ne choisit ni la mise en avant ni la publication : c'est HORIZON qui valide.
            val clean = Validation.game(game.copy(id = id, publisher = p.companyName.take(80), published = false, featured = false))
            val owner = io { store.ownerOfGame(id) }
            if (owner != null && owner.first?.toString() != p.id) throw conflict("id_taken", "Cet identifiant de jeu est déjà pris.")
            if (owner != null && owner.second !in setOf("draft", "rejected")) {
                throw conflict("locked", "Ce jeu est en cours de validation ou déjà publié : il ne peut plus être modifié ici.")
            }
            io { store.saveGameForPartner(UUID.fromString(p.id), clean) }
            call.respond(clean)
        }

        post("/games/{id}/submit") {
            val p = call.publisherAuth(store)
            p.requireActive()
            val id = Validation.gameId(call.parameters["id"] ?: "")
            val owner = io { store.ownerOfGame(id) }
            if (owner == null || owner.first?.toString() != p.id) throw notFound("game_not_found", "Jeu introuvable.")
            val g = io { store.getGame(id, includeUnpublished = true) }!!
            if (g.packUrl == null || g.packSha256 == null) throw badRequest("incomplete", "Ajoute l'adresse du fichier de jeu et son empreinte SHA-256 avant d'envoyer.")
            if (g.description.trim().length < 20) throw badRequest("incomplete", "La description doit faire au moins 20 caractères.")
            if (g.priceCents == null) throw badRequest("incomplete", "Indique un prix (0 pour un jeu gratuit).")
            if (!io { store.submitGame(UUID.fromString(p.id), id) }) throw conflict("not_submittable", "Ce jeu est déjà en cours de validation ou publié.")
            call.respond(mapOf("status" to "pending"))
        }

        post("/codes") {
            val p = call.publisherAuth(store)
            p.requireActive()
            val req = call.body<PartnerCodesRequest>()
            Validation.gameId(req.gameId)
            if (req.count !in 1..500) throw badRequest("invalid_count", "Nombre de codes : de 1 à 500.")
            if (req.expiresInDays != null && req.expiresInDays !in 1..3650) throw badRequest("invalid_expiry", "Durée de validité invalide.")
            val ref = req.reference.trim()
            if (ref.length !in 1..80) throw badRequest("invalid_reference", "Référence du lot : 1 à 80 caractères.")
            if (!limiter.allow("pub-batch:${p.id}", 60, 3_600_000)) throw tooMany()
            val owner = io { store.ownerOfGame(req.gameId) }
            if (owner == null || owner.first?.toString() != p.id || owner.second != "approved") {
                throw ApiException(HttpStatusCode.Forbidden, "game_not_allowed", "Les codes ne peuvent être créés que pour tes jeux déjà validés par HORIZON.")
            }
            val codes = List(req.count) { Security.newRedeemCode() }
            val expires = req.expiresInDays?.let { Instant.now().plus(it.toLong(), ChronoUnit.DAYS) }
            val created = io {
                store.createPartnerCodes(UUID.fromString(p.id), req.gameId, ref, codes.map { Security.sha256Hex(Security.normalizeCode(it)) }, expires)
            }
            if (!created) throw conflict("duplicate_reference", "Cette référence de lot existe déjà : les codes n'ont pas été recréés. Utilise une autre référence.")
            call.respond(HttpStatusCode.Created, CreateCodesResponse(req.gameId, codes))
        }

        get("/sales") {
            val p = call.publisherAuth(store)
            if (p.status != "active" && p.status != "suspended") p.requireActive()
            call.respond(io { salesFor(store, p) })
        }
    }
}

private fun ApplicationCall.adminCheck(config: AppConfig, limiter: RateLimiter) {
    val expected = config.adminToken ?: throw notFound("not_found", "Introuvable.")
    val ip = clientIp(config.trustProxy)
    if (limiter.count("admin-fail:$ip", 900_000) >= 10) throw tooMany()
    val given = request.headers["X-Admin-Token"] ?: ""
    if (!Security.constantTimeEquals(expected, given)) {
        limiter.allow("admin-fail:$ip", Int.MAX_VALUE, 900_000)
        throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Jeton administrateur invalide.")
    }
}

/** Administration des éditeurs : contrats, validation des jeux, reversements. */
fun Route.adminPublisherRoutes(config: AppConfig, store: Store, limiter: RateLimiter) {
    route("/admin/publishers") {
        get {
            call.adminCheck(config, limiter)
            call.respond(AdminPublishersResponse(io { store.listPublishers() }))
        }
        post("/{id}/contract-offer") {
            call.adminCheck(config, limiter)
            val id = call.pathUuid("id")
            val req = call.body<ContractOfferRequest>()
            if (req.commissionPercent.isNaN() || req.commissionPercent < 0.0 || req.commissionPercent > 95.0) {
                throw badRequest("invalid_commission", "Commission : entre 0 et 95 %.")
            }
            val bp = Math.round(req.commissionPercent * 100).toInt()
            if (!io { store.offerContract(id, bp) }) throw conflict("bad_state", "Les conditions ne peuvent plus être modifiées : le contrat est déjà signé.")
            call.respond(io { store.findPublisher(id) }!!)
        }
        post("/{id}/validate") {
            call.adminCheck(config, limiter)
            val id = call.pathUuid("id")
            val req = call.body<NoteRequest>()
            if (!io { store.validateContract(id, req.note) }) throw conflict("bad_state", "Seul un contrat signé peut être validé.")
            call.respond(io { store.findPublisher(id) }!!)
        }
        post("/{id}/suspend") {
            call.adminCheck(config, limiter)
            val id = call.pathUuid("id")
            if (!io { store.suspendPublisher(id) }) throw conflict("bad_state", "Seul un compte actif peut être suspendu.")
            call.respond(io { store.findPublisher(id) }!!)
        }
        post("/{id}/reinstate") {
            call.adminCheck(config, limiter)
            val id = call.pathUuid("id")
            if (!io { store.reinstatePublisher(id) }) throw conflict("bad_state", "Seul un compte suspendu peut être réactivé.")
            call.respond(io { store.findPublisher(id) }!!)
        }
        get("/{id}/sales") {
            call.adminCheck(config, limiter)
            val id = call.pathUuid("id")
            val p = io { store.findPublisher(id) } ?: throw notFound("not_found", "Éditeur introuvable.")
            call.respond(io { salesFor(store, p) })
        }
        post("/{id}/payouts") {
            call.adminCheck(config, limiter)
            val id = call.pathUuid("id")
            val req = call.body<PayoutRequest>()
            if (req.amountCents <= 0 || req.amountCents > 1_000_000_000L) throw badRequest("invalid_amount", "Montant invalide.")
            if (req.currency !in setOf("USD", "EUR", "CDF")) throw badRequest("invalid_currency", "Devise : USD, EUR ou CDF.")
            io { store.findPublisher(id) } ?: throw notFound("not_found", "Éditeur introuvable.")
            io { store.addPayout(id, req.amountCents, req.currency, req.note) }
            call.respond(HttpStatusCode.Created, mapOf("status" to "recorded"))
        }
    }

    get("/admin/reviews") {
        call.adminCheck(config, limiter)
        call.respond(AdminReviewsResponse(io { store.pendingReviews() }))
    }
    post("/admin/games/{id}/review") {
        call.adminCheck(config, limiter)
        val id = Validation.gameId(call.parameters["id"] ?: "")
        val req = call.body<ReviewRequest>()
        if (!req.approve && req.note.isNullOrBlank()) throw badRequest("note_required", "Indique le motif du refus : l'éditeur le verra.")
        if (!io { store.reviewGame(id, req.approve, req.note) }) throw conflict("bad_state", "Ce jeu n'est pas en attente de validation.")
        call.respond(mapOf("status" to if (req.approve) "approved" else "rejected"))
    }
}
