package com.horizon.game.data.remote

import com.horizon.game.domain.model.Compatibility
import com.horizon.game.domain.model.Game
import com.horizon.game.domain.model.GameCategory
import org.json.JSONObject

/** Profil HORIZON ID tel que renvoyé par le serveur. */
data class UserInfo(
    val id: String,
    val email: String?,
    val emailVerified: Boolean,
    val phone: String?,
    val phoneVerified: Boolean,
    val displayName: String,
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("email", email ?: JSONObject.NULL)
        .put("emailVerified", emailVerified)
        .put("phone", phone ?: JSONObject.NULL)
        .put("phoneVerified", phoneVerified)
        .put("displayName", displayName)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = UserInfo(
            id = o.getString("id"),
            email = o.stringOrNull("email"),
            emailVerified = o.optBoolean("emailVerified", false),
            phone = o.stringOrNull("phone"),
            phoneVerified = o.optBoolean("phoneVerified", false),
            displayName = o.getString("displayName"),
            createdAt = o.optString("createdAt", "")
        )
    }
}

/** Convertit un jeu JSON du serveur ; lève une exception si un champ obligatoire manque ou est inconnu. */
fun gameFromJson(o: JSONObject) = Game(
    id = o.getString("id"),
    title = o.getString("title"),
    publisher = o.getString("publisher"),
    category = GameCategory.valueOf(o.getString("category")),
    description = o.optString("description", ""),
    ageRating = o.optString("ageRating", ""),
    sizeMb = o.optInt("sizeMb", 0),
    compatibility = runCatching { Compatibility.valueOf(o.getString("compatibility")) }.getOrDefault(Compatibility.UNKNOWN),
    packageName = o.stringOrNull("packageName"),
    apkUrl = o.stringOrNull("apkUrl"),
    apkSha256 = o.stringOrNull("apkSha256"),
    packUrl = o.stringOrNull("packUrl"),
    packSha256 = o.stringOrNull("packSha256"),
    versionCode = o.optInt("versionCode", 1),
    versionName = o.optString("versionName", "1.0"),
    engine = o.stringOrNull("engine"),
    engineVersion = o.stringOrNull("engineVersion"),
    priceCents = if (o.has("priceCents") && !o.isNull("priceCents")) o.getInt("priceCents") else null,
    currency = o.optString("currency", "USD"),
    featured = o.optBoolean("featured", false)
)
