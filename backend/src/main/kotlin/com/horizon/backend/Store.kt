package com.horizon.backend

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class CodeRow(val id: UUID, val codeHash: String, val attempts: Int)

/** Accès PostgreSQL en JDBC simple. Toutes les requêtes sont paramétrées (pas d'injection SQL). */
class Store(private val ds: DataSource) {

    private inline fun <T> withConn(block: (Connection) -> T): T = ds.connection.use(block)

    companion object {
        private const val USER_COLS =
            "id,email,email_verified,phone,phone_verified,display_name,avatar_url,password_hash,status,language,theme,notifications,created_at"
        private const val GAME_COLS =
            "id,title,publisher,category,description,age_rating,size_mb,compatibility,package_name,apk_url,apk_sha256,featured,published"

        fun isUniqueViolation(e: SQLException) = e.sqlState == "23505"
    }

    private fun ResultSet.toUser() = UserRow(
        id = getObject("id", UUID::class.java),
        email = getString("email"),
        emailVerified = getBoolean("email_verified"),
        phone = getString("phone"),
        phoneVerified = getBoolean("phone_verified"),
        displayName = getString("display_name"),
        avatarUrl = getString("avatar_url"),
        passwordHash = getString("password_hash"),
        status = getString("status"),
        language = getString("language"),
        theme = getString("theme"),
        notifications = getBoolean("notifications"),
        createdAt = getTimestamp("created_at").toInstant()
    )

    private fun ResultSet.toGame() = GameDto(
        id = getString("id"),
        title = getString("title"),
        publisher = getString("publisher"),
        category = getString("category"),
        description = getString("description"),
        ageRating = getString("age_rating"),
        sizeMb = getInt("size_mb"),
        compatibility = getString("compatibility"),
        packageName = getString("package_name"),
        apkUrl = getString("apk_url"),
        apkSha256 = getString("apk_sha256"),
        featured = getBoolean("featured"),
        published = getBoolean("published")
    )

    // ---------- Utilisateurs ----------

    /** Lève SQLException (état 23505) si l'e-mail ou le téléphone existe déjà. */
    fun createUser(email: String?, phone: String?, displayName: String, passwordHash: String): UserRow = withConn { c ->
        val id = UUID.randomUUID()
        c.prepareStatement(
            "INSERT INTO users (id,email,phone,display_name,password_hash) VALUES (?,?,?,?,?)"
        ).use { ps ->
            ps.setObject(1, id)
            ps.setString(2, email)
            ps.setString(3, phone)
            ps.setString(4, displayName)
            ps.setString(5, passwordHash)
            ps.executeUpdate()
        }
        findUserById(c, id) ?: error("Utilisateur introuvable après création")
    }

    private fun findUserById(c: Connection, id: UUID): UserRow? =
        c.prepareStatement("SELECT $USER_COLS FROM users WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }

    fun findUserById(id: UUID): UserRow? = withConn { findUserById(it, id) }

    fun findUserByEmail(email: String): UserRow? = withConn { c ->
        c.prepareStatement("SELECT $USER_COLS FROM users WHERE email = ?").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    fun findUserByPhone(phone: String): UserRow? = withConn { c ->
        c.prepareStatement("SELECT $USER_COLS FROM users WHERE phone = ?").use { ps ->
            ps.setString(1, phone)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    fun updateUser(id: UUID, req: UpdateMeRequest): UserRow = withConn { c ->
        val current = findUserById(c, id) ?: throw unauthorized()
        c.prepareStatement(
            "UPDATE users SET display_name=?, avatar_url=?, language=?, theme=?, notifications=? WHERE id=?"
        ).use { ps ->
            ps.setString(1, req.displayName ?: current.displayName)
            ps.setString(2, req.avatarUrl ?: current.avatarUrl)
            ps.setString(3, req.language ?: current.language)
            ps.setString(4, req.theme ?: current.theme)
            ps.setBoolean(5, req.notifications ?: current.notifications)
            ps.setObject(6, id)
            ps.executeUpdate()
        }
        findUserById(c, id) ?: error("Utilisateur introuvable")
    }

    fun deleteUser(id: UUID) = withConn { c ->
        c.prepareStatement("DELETE FROM users WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeUpdate()
        }
    }

    fun markVerified(userId: UUID, channel: String) = withConn { c ->
        val column = if (channel == "phone") "phone_verified" else "email_verified"
        c.prepareStatement("UPDATE users SET $column = TRUE WHERE id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeUpdate()
        }
    }

    // ---------- Sessions ----------

    fun createSession(userId: UUID, tokenHash: String, deviceLabel: String?, expiresAt: Instant) = withConn { c ->
        c.prepareStatement(
            "INSERT INTO device_sessions (id,user_id,token_hash,device_label,expires_at) VALUES (?,?,?,?,?)"
        ).use { ps ->
            ps.setObject(1, UUID.randomUUID())
            ps.setObject(2, userId)
            ps.setString(3, tokenHash)
            ps.setString(4, deviceLabel?.take(80))
            ps.setTimestamp(5, Timestamp.from(expiresAt))
            ps.executeUpdate()
        }
    }

    fun findSession(tokenHash: String): AuthContext? = withConn { c ->
        val row = c.prepareStatement(
            "SELECT id, user_id FROM device_sessions WHERE token_hash = ? AND revoked = FALSE AND expires_at > now()"
        ).use { ps ->
            ps.setString(1, tokenHash)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getObject("id", UUID::class.java) to rs.getObject("user_id", UUID::class.java) else null
            }
        } ?: return@withConn null
        val user = findUserById(c, row.second) ?: return@withConn null
        if (user.status != "active") return@withConn null
        c.prepareStatement("UPDATE device_sessions SET last_seen_at = now() WHERE id = ?").use { ps ->
            ps.setObject(1, row.first)
            ps.executeUpdate()
        }
        AuthContext(user, row.first)
    }

    fun revokeSession(sessionId: UUID) = withConn { c ->
        c.prepareStatement("UPDATE device_sessions SET revoked = TRUE WHERE id = ?").use { ps ->
            ps.setObject(1, sessionId)
            ps.executeUpdate()
        }
    }

    // ---------- Codes de vérification ----------

    fun createCode(userId: UUID, channel: String, codeHash: String, expiresAt: Instant) = withConn { c ->
        c.prepareStatement("UPDATE verification_codes SET used = TRUE WHERE user_id = ? AND channel = ? AND used = FALSE").use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, channel)
            ps.executeUpdate()
        }
        c.prepareStatement(
            "INSERT INTO verification_codes (id,user_id,channel,code_hash,expires_at) VALUES (?,?,?,?,?)"
        ).use { ps ->
            ps.setObject(1, UUID.randomUUID())
            ps.setObject(2, userId)
            ps.setString(3, channel)
            ps.setString(4, codeHash)
            ps.setTimestamp(5, Timestamp.from(expiresAt))
            ps.executeUpdate()
        }
    }

    fun activeCode(userId: UUID, channel: String): CodeRow? = withConn { c ->
        c.prepareStatement(
            "SELECT id, code_hash, attempts FROM verification_codes " +
                "WHERE user_id = ? AND channel = ? AND used = FALSE AND expires_at > now() " +
                "ORDER BY created_at DESC LIMIT 1"
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, channel)
            ps.executeQuery().use { rs ->
                if (rs.next()) CodeRow(rs.getObject("id", UUID::class.java), rs.getString("code_hash"), rs.getInt("attempts")) else null
            }
        }
    }

    fun bumpCodeAttempts(codeId: UUID) = withConn { c ->
        c.prepareStatement("UPDATE verification_codes SET attempts = attempts + 1 WHERE id = ?").use { ps ->
            ps.setObject(1, codeId)
            ps.executeUpdate()
        }
    }

    fun markCodeUsed(codeId: UUID) = withConn { c ->
        c.prepareStatement("UPDATE verification_codes SET used = TRUE WHERE id = ?").use { ps ->
            ps.setObject(1, codeId)
            ps.executeUpdate()
        }
    }

    // ---------- Jeux ----------

    fun listGames(query: String?, category: String?, page: Int, pageSize: Int): GameListResponse = withConn { c ->
        val where = mutableListOf("published = TRUE")
        val params = mutableListOf<Any>()
        if (!query.isNullOrBlank()) {
            val like = "%" + query.trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            where += "(lower(title) LIKE ? OR lower(publisher) LIKE ?)"
            params += like
            params += like
        }
        if (!category.isNullOrBlank()) {
            where += "category = ?"
            params += category
        }
        val clause = where.joinToString(" AND ")

        val total = c.prepareStatement("SELECT count(*) FROM games WHERE $clause").use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val items = c.prepareStatement(
            "SELECT $GAME_COLS FROM games WHERE $clause ORDER BY featured DESC, title ASC LIMIT ? OFFSET ?"
        ).use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.setInt(params.size + 1, pageSize)
            ps.setInt(params.size + 2, (page - 1) * pageSize)
            ps.executeQuery().use { rs ->
                val list = ArrayList<GameDto>()
                while (rs.next()) list += rs.toGame()
                list
            }
        }
        GameListResponse(items, total, page, pageSize)
    }

    fun getGame(id: String, includeUnpublished: Boolean = false): GameDto? = withConn { c ->
        val extra = if (includeUnpublished) "" else " AND published = TRUE"
        c.prepareStatement("SELECT $GAME_COLS FROM games WHERE id = ?$extra").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toGame() else null }
        }
    }

    /** Tous les jeux, publiés ou non (administration). */
    fun listAllGames(): GameListResponse = withConn { c ->
        val items = c.prepareStatement("SELECT $GAME_COLS FROM games ORDER BY featured DESC, title ASC").use { ps ->
            ps.executeQuery().use { rs ->
                val list = ArrayList<GameDto>()
                while (rs.next()) list += rs.toGame()
                list
            }
        }
        GameListResponse(items, items.size, 1, items.size)
    }

    fun stats(): AdminStats = withConn { c ->
        fun count(sql: String): Int = c.createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getInt(1) } }
        AdminStats(
            users = count("SELECT count(*) FROM users"),
            games = count("SELECT count(*) FROM games"),
            activeSessions = count("SELECT count(*) FROM device_sessions WHERE revoked = FALSE AND expires_at > now()")
        )
    }

    fun countGames(): Int = withConn { c ->
        c.createStatement().use { st -> st.executeQuery("SELECT count(*) FROM games").use { rs -> rs.next(); rs.getInt(1) } }
    }

    fun upsertGame(g: GameDto) = withConn { c ->
        c.prepareStatement(
            """
            INSERT INTO games (id,title,publisher,category,description,age_rating,size_mb,compatibility,package_name,apk_url,apk_sha256,featured,published)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (id) DO UPDATE SET
              title=EXCLUDED.title, publisher=EXCLUDED.publisher, category=EXCLUDED.category,
              description=EXCLUDED.description, age_rating=EXCLUDED.age_rating, size_mb=EXCLUDED.size_mb,
              compatibility=EXCLUDED.compatibility, package_name=EXCLUDED.package_name, apk_url=EXCLUDED.apk_url,
              apk_sha256=EXCLUDED.apk_sha256, featured=EXCLUDED.featured, published=EXCLUDED.published,
              updated_at=now()
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, g.id)
            ps.setString(2, g.title)
            ps.setString(3, g.publisher)
            ps.setString(4, g.category)
            ps.setString(5, g.description)
            ps.setString(6, g.ageRating)
            ps.setInt(7, g.sizeMb)
            ps.setString(8, g.compatibility)
            ps.setString(9, g.packageName)
            ps.setString(10, g.apkUrl)
            ps.setString(11, g.apkSha256)
            ps.setBoolean(12, g.featured)
            ps.setBoolean(13, g.published)
            ps.executeUpdate()
        }
    }

    fun deleteGame(id: String): Boolean = withConn { c ->
        c.prepareStatement("DELETE FROM games WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate() > 0
        }
    }

    // ---------- Favoris ----------

    fun favorites(userId: UUID): List<String> = withConn { c ->
        c.prepareStatement("SELECT game_id FROM user_favorites WHERE user_id = ? ORDER BY created_at").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                val list = ArrayList<String>()
                while (rs.next()) list += rs.getString(1)
                list
            }
        }
    }

    /** Remplace tous les favoris ; les identifiants de jeux inconnus sont ignorés. */
    fun replaceFavorites(userId: UUID, gameIds: List<String>): List<String> = withConn { c ->
        c.autoCommit = false
        try {
            c.prepareStatement("DELETE FROM user_favorites WHERE user_id = ?").use { ps ->
                ps.setObject(1, userId)
                ps.executeUpdate()
            }
            c.prepareStatement(
                "INSERT INTO user_favorites (user_id, game_id) SELECT ?, id FROM games WHERE id = ? ON CONFLICT DO NOTHING"
            ).use { ps ->
                for (gid in gameIds.distinct().take(500)) {
                    ps.setObject(1, userId)
                    ps.setString(2, gid)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
        favorites(userId)
    }

    fun addFavorite(userId: UUID, gameId: String): Boolean = withConn { c ->
        c.prepareStatement(
            "INSERT INTO user_favorites (user_id, game_id) SELECT ?, id FROM games WHERE id = ? ON CONFLICT DO NOTHING"
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, gameId)
            ps.executeUpdate()
        }
        getGame(gameId) != null
    }

    fun removeFavorite(userId: UUID, gameId: String) = withConn { c ->
        c.prepareStatement("DELETE FROM user_favorites WHERE user_id = ? AND game_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, gameId)
            ps.executeUpdate()
        }
    }
}
