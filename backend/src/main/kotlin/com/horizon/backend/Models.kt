package com.horizon.backend

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Erreur métier renvoyée au client sous la forme {"error": code, "message": texte}. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String
) : RuntimeException(message)

fun badRequest(code: String, message: String) = ApiException(HttpStatusCode.BadRequest, code, message)
fun unauthorized(message: String = "Connexion requise.") =
    ApiException(HttpStatusCode.Unauthorized, "unauthorized", message)

@Serializable
data class ErrorBody(val error: String, val message: String)

// ---------- Comptes ----------

@Serializable
data class RegisterRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
    val displayName: String,
    val deviceLabel: String? = null
)

@Serializable
data class LoginRequest(
    /** Adresse e-mail ou numéro de téléphone international. */
    val identifier: String,
    val password: String,
    val deviceLabel: String? = null
)

@Serializable
data class PreferencesDto(val language: String, val theme: String, val notifications: Boolean)

@Serializable
data class UserDto(
    val id: String,
    val email: String?,
    val emailVerified: Boolean,
    val phone: String?,
    val phoneVerified: Boolean,
    val displayName: String,
    val avatarUrl: String?,
    val createdAt: String,
    val preferences: PreferencesDto
)

@Serializable
data class AuthResponse(val token: String, val expiresAt: String, val user: UserDto)

@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val language: String? = null,
    val theme: String? = null,
    val notifications: Boolean? = null
)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class VerifyRequest(val channel: String)

@Serializable
data class VerifyConfirmRequest(val channel: String, val code: String)

// ---------- Store ----------

@Serializable
data class GameDto(
    val id: String,
    val title: String,
    val publisher: String,
    /** ACTION, RACING, PUZZLE, ADVENTURE ou STRATEGY. */
    val category: String,
    val description: String,
    val ageRating: String,
    val sizeMb: Int,
    /** VERIFIED, DECLARED ou UNKNOWN. */
    val compatibility: String,
    val packageName: String? = null,
    val apkUrl: String? = null,
    /** Empreinte SHA-256 de l'APK : l'application la vérifie après téléchargement. */
    val apkSha256: String? = null,
    val featured: Boolean = false,
    val published: Boolean = true,
    /** Paquet de jeu téléchargé dans l'application (visible seulement par les joueurs qui possèdent le jeu). */
    val packUrl: String? = null,
    val packSha256: String? = null,
    /** Numéro de version croissant : sert à détecter les mises à jour. */
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    /** Moteur de jeu (ex. "godot") et sa version, pour savoir quel lecteur utiliser. */
    val engine: String? = null,
    val engineVersion: String? = null,
    /** Prix en centimes : absent = pas en vente (code seulement), 0 = gratuit. */
    val priceCents: Int? = null,
    val currency: String = "USD"
) {
    /** Version publique du catalogue : sans aucun lien de téléchargement. */
    fun toPublic() = copy(apkUrl = null, apkSha256 = null, packUrl = null, packSha256 = null)
}

@Serializable
data class GameListResponse(val items: List<GameDto>, val total: Int, val page: Int, val pageSize: Int)

@Serializable
data class RedeemRequest(val code: String)

@Serializable
data class CreateCodesRequest(val gameId: String, val count: Int, val expiresInDays: Int? = null)

@Serializable
data class CreateCodesResponse(val gameId: String, val codes: List<String>)

@Serializable
data class CodeStats(val total: Int, val redeemed: Int)

@Serializable
data class CreateOrderRequest(val gameId: String, val provider: String = "sandbox")

@Serializable
data class OrderDto(
    val id: String,
    val gameId: String,
    val gameTitle: String,
    val amountCents: Int,
    val currency: String,
    val provider: String,
    /** pending, paid, failed ou cancelled. Seul le serveur change ce statut. */
    val status: String,
    val createdAt: String,
    val paidAt: String? = null,
    val sandbox: Boolean = false,
    val message: String? = null
)

@Serializable
data class AdminOrderDto(val order: OrderDto, val userName: String?)

@Serializable
data class AdminOrdersResponse(val items: List<AdminOrderDto>)

@Serializable
data class PartnerDto(
    val id: String,
    val name: String,
    /** publisher (éditeur) ou reseller (revendeur). */
    val kind: String,
    val active: Boolean,
    val gameIds: List<String>,
    val createdAt: String,
    val codesCreated: Int,
    val codesRedeemed: Int
)

@Serializable
data class CreatePartnerRequest(val name: String, val kind: String, val gameIds: List<String> = emptyList())

@Serializable
data class UpdatePartnerRequest(val active: Boolean? = null, val gameIds: List<String>? = null)

/** La clé d'accès n'est montrée qu'une seule fois, à la création ou au renouvellement. */
@Serializable
data class PartnerKeyResponse(val partner: PartnerDto, val apiKey: String)

@Serializable
data class PartnersResponse(val items: List<PartnerDto>)

@Serializable
data class PartnerCodesRequest(
    val gameId: String,
    val count: Int,
    val expiresInDays: Int? = null,
    /** Identifiant unique de ce lot de ton côté (ex. numéro de commande) : évite les doublons si la requête est rejouée. */
    val reference: String
)

@Serializable
data class PartnerGameStats(val gameId: String, val title: String, val total: Int, val redeemed: Int)

@Serializable
data class PartnerGamesResponse(val partner: String, val kind: String, val games: List<PartnerGameStats>)

data class PartnerRow(val id: UUID, val name: String, val kind: String, val active: Boolean)

@Serializable
data class AdminStats(val users: Int, val games: Int, val activeSessions: Int)

@Serializable
data class FavoritesResponse(val gameIds: List<String>)

@Serializable
data class SyncFavoritesRequest(val gameIds: List<String>)

@Serializable
data class ConfigResponse(
    val apiVersion: String,
    val minAppVersion: String,
    val latestAppVersion: String,
    val features: Map<String, Boolean>,
    val serverTime: String
)

// ---------- Lignes de base de données ----------

data class UserRow(
    val id: UUID,
    val email: String?,
    val emailVerified: Boolean,
    val phone: String?,
    val phoneVerified: Boolean,
    val displayName: String,
    val avatarUrl: String?,
    val passwordHash: String,
    val status: String,
    val language: String,
    val theme: String,
    val notifications: Boolean,
    val createdAt: Instant
) {
    fun toDto() = UserDto(
        id = id.toString(),
        email = email,
        emailVerified = emailVerified,
        phone = phone,
        phoneVerified = phoneVerified,
        displayName = displayName,
        avatarUrl = avatarUrl,
        createdAt = createdAt.toString(),
        preferences = PreferencesDto(language, theme, notifications)
    )
}

data class AuthContext(val user: UserRow, val sessionId: UUID)
