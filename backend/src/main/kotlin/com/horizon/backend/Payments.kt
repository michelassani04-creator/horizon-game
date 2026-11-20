package com.horizon.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Instruction donnée au joueur après la création d'une commande (selon le fournisseur). */
data class PaymentInstruction(val message: String)

data class WebhookResult(val orderId: UUID, val paid: Boolean)

/**
 * Moteur de paiement indépendant des fournisseurs. Chaque fournisseur (M-Pesa, Orange Money, Airtel Money,
 * carte…) implémentera cette interface. Règle : une commande n'est « payée » QUE lorsque [verifyWebhook]
 * a authentifié un message du fournisseur, jamais sur la parole de l'application.
 */
interface PaymentProvider {
    val id: String
    val sandbox: Boolean
    fun initiate(order: OrderDto): PaymentInstruction

    /** Vérifie l'authenticité d'une notification du fournisseur ; null si elle n'est pas fiable. */
    fun verifyWebhook(header: (String) -> String?, body: String): WebhookResult?
}

@Serializable
private data class SandboxWebhookBody(val orderId: String, val status: String)

/**
 * Fournisseur de TEST : aucun argent réel. La confirmation passe par la page /admin (bouton « simuler »)
 * ou par une notification signée (HMAC-SHA256 dans l'en-tête X-Horizon-Signature) comme le ferait un vrai fournisseur.
 */
class SandboxProvider(private val secret: String?) : PaymentProvider {
    override val id = "sandbox"
    override val sandbox = true

    override fun initiate(order: OrderDto) =
        PaymentInstruction("Paiement de TEST : aucun argent réel. En attente de confirmation côté serveur.")

    override fun verifyWebhook(header: (String) -> String?, body: String): WebhookResult? {
        val key = secret ?: return null
        val given = header("X-Horizon-Signature") ?: return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        if (!Security.constantTimeEquals(expected, given.lowercase())) return null
        val parsed = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(SandboxWebhookBody.serializer(), body)
        } catch (e: Exception) {
            return null
        }
        val orderId = runCatching { UUID.fromString(parsed.orderId) }.getOrNull() ?: return null
        return WebhookResult(orderId, parsed.status == "paid")
    }
}
