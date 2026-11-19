package com.horizon.game.data.remote

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
