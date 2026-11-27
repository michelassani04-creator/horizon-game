package com.horizon.backend

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Horizon")

/** Taille maximale d'un corps de requête : aucune route n'a besoin de plus (descriptions de 4000 caractères, 500 favoris…). */
private const val MAX_BODY_BYTES = 64L * 1024

/**
 * Refuse les corps trop gros avant de les lire en mémoire. Un corps « chunked » n'annonce pas sa taille :
 * il est refusé (les clients de l'application et des navigateurs envoient toujours Content-Length).
 */
private val BodyLimit = createApplicationPlugin("BodyLimit") {
    onCall { call ->
        val headers = call.request.headers
        if (headers[HttpHeaders.TransferEncoding]?.contains("chunked", ignoreCase = true) == true) {
            throw ApiException(HttpStatusCode.LengthRequired, "length_required", "En-tête Content-Length obligatoire.")
        }
        val length = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (length != null && length > MAX_BODY_BYTES) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, "payload_too_large", "Requête trop volumineuse.")
        }
    }
}

fun main() {
    val config = AppConfig.fromEnv()
    val ds = Database.connect(config)
    Database.migrate(ds)
    val store = Store(ds)
    if (config.seedDemo) Seed.run(store)
    if (config.adminToken == null) {
        log.warn("ADMIN_TOKEN absent ou trop court (< 24 caractères) : la publication de jeux est désactivée.")
    }
    log.info("HORIZON backend démarré sur le port {}", config.port)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config, store)
    }.start(wait = true)
}

fun Application.module(config: AppConfig, store: Store) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(CallLogging)
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorBody(cause.code, cause.message))
        }
        exception<Throwable> { call, cause ->
            log.error("Erreur non gérée", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorBody("internal_error", "Erreur interne du serveur."))
        }
    }
    // Après StatusPages, pour que ses refus soient renvoyés au format JSON habituel.
    install(BodyLimit)
    installRoutes(config, store)
}
