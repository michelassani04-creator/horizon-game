package com.horizon.backend

import kotlinx.serialization.Serializable

@Serializable
data class PublisherRegisterRequest(
    val email: String,
    val password: String,
    val companyName: String,
    val contactName: String,
    val phone: String? = null,
    val country: String
)

@Serializable
data class PublisherLoginRequest(val email: String, val password: String)

@Serializable
data class PublisherDto(
    val id: String,
    val email: String?,
    val companyName: String,
    val contactName: String,
    val phone: String?,
    val country: String,
    /** registered, contract_ready, signed, active ou suspended. */
    val status: String,
    val commissionPercent: Double?,
    val contractVersion: String?,
    val signedName: String?,
    val signedAt: String?,
    val validatedAt: String?,
    val createdAt: String
)

@Serializable
data class PublisherAuthResponse(val token: String, val expiresAt: String, val publisher: PublisherDto)

@Serializable
data class ContractDto(
    val version: String,
    val text: String,
    val sha256: String,
    val commissionPercent: Double,
    val status: String,
    val signedName: String?,
    val signedAt: String?
)

@Serializable
data class SignContractRequest(val signedName: String, val accept: Boolean, val sha256: String)

@Serializable
data class PublisherGameDto(
    val game: GameDto,
    /** draft, pending, approved ou rejected. */
    val reviewStatus: String,
    val reviewNote: String?,
    val codesCreated: Int,
    val codesRedeemed: Int
)

@Serializable
data class PublisherGamesResponse(val items: List<PublisherGameDto>)

@Serializable
data class SalesLine(
    val gameId: String,
    val title: String,
    val currency: String,
    /** true = vente de test, sans argent réel. */
    val test: Boolean,
    val orders: Int,
    val grossCents: Long
)

@Serializable
data class BalanceLine(
    val currency: String,
    val grossCents: Long,
    val commissionCents: Long,
    val dueCents: Long,
    val paidCents: Long,
    val balanceCents: Long
)

@Serializable
data class PayoutDto(val id: String, val amountCents: Long, val currency: String, val note: String?, val createdAt: String)

@Serializable
data class SalesResponse(val lines: List<SalesLine>, val balances: List<BalanceLine>, val payouts: List<PayoutDto>)

@Serializable
data class AdminPublisherDto(val publisher: PublisherDto, val games: Int, val codesCreated: Int, val codesRedeemed: Int)

@Serializable
data class AdminPublishersResponse(val items: List<AdminPublisherDto>)

@Serializable
data class ContractOfferRequest(val commissionPercent: Double)

@Serializable
data class NoteRequest(val note: String? = null)

@Serializable
data class ReviewRequest(val approve: Boolean, val note: String? = null)

@Serializable
data class PayoutRequest(val amountCents: Long, val currency: String, val note: String? = null)

@Serializable
data class AdminReviewItem(val game: GameDto, val publisherName: String?, val reviewStatus: String, val reviewNote: String?)

@Serializable
data class AdminReviewsResponse(val items: List<AdminReviewItem>)

/** Calcul de la part de l'éditeur : pure, donc testable. Les ventes de test ne comptent pas. */
object Revenue {
    fun balances(lines: List<SalesLine>, payouts: List<PayoutDto>, commissionBp: Int): List<BalanceLine> {
        val currencies = (lines.filter { !it.test }.map { it.currency } + payouts.map { it.currency }).distinct().sorted()
        return currencies.map { cur ->
            val gross = lines.filter { !it.test && it.currency == cur }.sumOf { it.grossCents }
            val commission = Math.round(gross * commissionBp / 10000.0)
            val paid = payouts.filter { it.currency == cur }.sumOf { it.amountCents }
            BalanceLine(cur, gross, commission, gross - commission, paid, gross - commission - paid)
        }
    }
}

/** Contrat d'éditeur : texte modèle + données de l'éditeur. Le texte exact signé est conservé en base. */
object Contract {
    const val VERSION = "1.0"

    private val template: String by lazy {
        object {}.javaClass.getResourceAsStream("/contract/v1.txt")?.bufferedReader()?.readText()
            ?: error("Modèle de contrat introuvable")
    }

    fun formatPercent(p: Double): String =
        (if (p % 1.0 == 0.0) p.toInt().toString() else String.format(java.util.Locale.ROOT, "%.2f", p).trimEnd('0').trimEnd('.'))
            .replace('.', ',')

    private val placeholder = Regex("\\{\\{([A-Z]+)}}")

    /**
     * Remplace les {{MARQUEURS}} en UNE seule passe : une valeur saisie par l'éditeur (par ex. un nom de studio
     * contenant « {{COMMISSION}} ») n'est jamais ré-interprétée comme un marqueur.
     */
    fun render(company: String, contact: String, email: String, country: String, commissionPercent: Double): String {
        val values = mapOf(
            "COMPANY" to company,
            "CONTACT" to contact,
            "EMAIL" to email,
            "COUNTRY" to country,
            "COMMISSION" to formatPercent(commissionPercent),
            "SHARE" to formatPercent(100.0 - commissionPercent)
        )
        return placeholder.replace(template) { m -> values[m.groupValues[1]] ?: m.value }
    }

    fun render(p: PublisherDto): String =
        render(p.companyName, p.contactName, p.email ?: "", p.country, p.commissionPercent ?: 0.0)
}
