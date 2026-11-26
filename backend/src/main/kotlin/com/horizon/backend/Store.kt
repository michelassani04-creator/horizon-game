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
            "id,title,publisher,category,description,age_rating,size_mb,compatibility,package_name,apk_url,apk_sha256,featured,published," +
                "pack_url,pack_sha256,version_code,version_name,engine,engine_version,price_cents,currency"

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
        published = getBoolean("published"),
        packUrl = getString("pack_url"),
        packSha256 = getString("pack_sha256"),
        versionCode = getInt("version_code"),
        versionName = getString("version_name"),
        engine = getString("engine"),
        engineVersion = getString("engine_version"),
        priceCents = getInt("price_cents").let { if (wasNull()) null else it },
        currency = getString("currency")
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
            INSERT INTO games (id,title,publisher,category,description,age_rating,size_mb,compatibility,package_name,apk_url,apk_sha256,featured,published,
                               pack_url,pack_sha256,version_code,version_name,engine,engine_version,price_cents,currency)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (id) DO UPDATE SET
              title=EXCLUDED.title, publisher=EXCLUDED.publisher, category=EXCLUDED.category,
              description=EXCLUDED.description, age_rating=EXCLUDED.age_rating, size_mb=EXCLUDED.size_mb,
              compatibility=EXCLUDED.compatibility, package_name=EXCLUDED.package_name, apk_url=EXCLUDED.apk_url,
              apk_sha256=EXCLUDED.apk_sha256, featured=EXCLUDED.featured, published=EXCLUDED.published,
              pack_url=EXCLUDED.pack_url, pack_sha256=EXCLUDED.pack_sha256, version_code=EXCLUDED.version_code,
              version_name=EXCLUDED.version_name, engine=EXCLUDED.engine, engine_version=EXCLUDED.engine_version,
              price_cents=EXCLUDED.price_cents, currency=EXCLUDED.currency,
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
            ps.setString(14, g.packUrl)
            ps.setString(15, g.packSha256)
            ps.setInt(16, g.versionCode)
            ps.setString(17, g.versionName)
            ps.setString(18, g.engine)
            ps.setString(19, g.engineVersion)
            if (g.priceCents == null) ps.setNull(20, java.sql.Types.INTEGER) else ps.setInt(20, g.priceCents)
            ps.setString(21, g.currency)
            ps.executeUpdate()
        }
    }

    fun deleteGame(id: String): Boolean = withConn { c ->
        c.prepareStatement("DELETE FROM games WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate() > 0
        }
    }

    // ---------- Éditeurs (portail) ----------

    private val pubCols =
        "id,email,name,contact_name,phone,country,status,commission_bp,contract_version,contract_signed_name," +
            "contract_signed_at,contract_validated_at,created_at"

    private fun ResultSet.toPublisher() = PublisherDto(
        id = getObject("id", UUID::class.java).toString(),
        email = getString("email"),
        companyName = getString("name"),
        contactName = getString("contact_name") ?: "",
        phone = getString("phone"),
        country = getString("country") ?: "",
        status = getString("status"),
        commissionPercent = getInt("commission_bp").let { if (wasNull()) null else it / 100.0 },
        contractVersion = getString("contract_version"),
        signedName = getString("contract_signed_name"),
        signedAt = getTimestamp("contract_signed_at")?.toInstant()?.toString(),
        validatedAt = getTimestamp("contract_validated_at")?.toInstant()?.toString(),
        createdAt = getTimestamp("created_at").toInstant().toString()
    )

    /** Lève SQLException (état 23505) si l'e-mail existe déjà. */
    fun createPublisher(
        email: String, passwordHash: String, company: String, contact: String, phone: String?, country: String
    ): UUID = withConn { c ->
        val id = UUID.randomUUID()
        c.prepareStatement(
            "INSERT INTO partners (id,name,kind,key_hash,active,email,password_hash,contact_name,phone,country,status) " +
                "VALUES (?,?,'publisher',NULL,TRUE,?,?,?,?,?,'registered')"
        ).use { ps ->
            ps.setObject(1, id); ps.setString(2, company); ps.setString(3, email); ps.setString(4, passwordHash)
            ps.setString(5, contact); ps.setString(6, phone); ps.setString(7, country)
            ps.executeUpdate()
        }
        id
    }

    fun findPublisherLogin(email: String): Pair<PublisherDto, String>? = withConn { c ->
        c.prepareStatement("SELECT $pubCols, password_hash FROM partners WHERE lower(email) = ?").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toPublisher() to (rs.getString("password_hash") ?: "") else null }
        }
    }

    fun findPublisher(id: UUID): PublisherDto? = withConn { c ->
        c.prepareStatement("SELECT $pubCols FROM partners WHERE id = ? AND email IS NOT NULL").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toPublisher() else null }
        }
    }

    fun createPublisherSession(partnerId: UUID, tokenHash: String, expiresAt: Instant) = withConn { c ->
        c.prepareStatement("INSERT INTO publisher_sessions (token_hash,partner_id,expires_at) VALUES (?,?,?)").use { ps ->
            ps.setString(1, tokenHash); ps.setObject(2, partnerId); ps.setTimestamp(3, Timestamp.from(expiresAt))
            ps.executeUpdate()
        }
    }

    fun findPublisherBySession(tokenHash: String): PublisherDto? = withConn { c ->
        c.prepareStatement(
            "SELECT $pubCols FROM partners WHERE email IS NOT NULL AND id = " +
                "(SELECT partner_id FROM publisher_sessions WHERE token_hash = ? AND expires_at > now())"
        ).use { ps ->
            ps.setString(1, tokenHash)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toPublisher() else null }
        }
    }

    fun deletePublisherSession(tokenHash: String) = withConn { c ->
        c.prepareStatement("DELETE FROM publisher_sessions WHERE token_hash = ?").use { ps ->
            ps.setString(1, tokenHash); ps.executeUpdate()
        }
    }

    private fun updateOne(sql: String, bind: (java.sql.PreparedStatement) -> Unit): Boolean = withConn { c ->
        c.prepareStatement(sql).use { ps -> bind(ps); ps.executeUpdate() > 0 }
    }

    /** L'administrateur fixe la commission : l'éditeur peut alors lire et signer le contrat. */
    fun offerContract(id: UUID, commissionBp: Int): Boolean = updateOne(
        "UPDATE partners SET commission_bp = ?, status = 'contract_ready' " +
            "WHERE id = ? AND email IS NOT NULL AND status IN ('registered','contract_ready')"
    ) { it.setInt(1, commissionBp); it.setObject(2, id) }

    fun validateContract(id: UUID, note: String?): Boolean = updateOne(
        "UPDATE partners SET status = 'active', active = TRUE, contract_validated_at = now(), contract_note = ? " +
            "WHERE id = ? AND email IS NOT NULL AND status = 'signed'"
    ) { it.setString(1, note?.take(500)); it.setObject(2, id) }

    fun suspendPublisher(id: UUID): Boolean = updateOne(
        "UPDATE partners SET status = 'suspended', active = FALSE WHERE id = ? AND email IS NOT NULL AND status = 'active'"
    ) { it.setObject(1, id) }

    fun reinstatePublisher(id: UUID): Boolean = updateOne(
        "UPDATE partners SET status = 'active', active = TRUE WHERE id = ? AND email IS NOT NULL AND status = 'suspended'"
    ) { it.setObject(1, id) }

    fun signContract(
        id: UUID, version: String, text: String, sha256: String, commissionBp: Int,
        signedName: String, ip: String?, agent: String?
    ): Boolean = withConn { c ->
        c.autoCommit = false
        try {
            val updated = c.prepareStatement(
                "UPDATE partners SET status = 'signed', contract_version = ?, contract_signed_name = ?, contract_signed_at = now() " +
                    "WHERE id = ? AND status = 'contract_ready'"
            ).use { ps -> ps.setString(1, version); ps.setString(2, signedName); ps.setObject(3, id); ps.executeUpdate() }
            if (updated == 0) {
                c.rollback()
                return@withConn false
            }
            c.prepareStatement(
                "INSERT INTO contract_signatures (id,partner_id,version,text_sha256,contract_text,commission_bp,signed_name,signed_ip,signed_agent) " +
                    "VALUES (?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                ps.setObject(1, UUID.randomUUID()); ps.setObject(2, id); ps.setString(3, version); ps.setString(4, sha256)
                ps.setString(5, text); ps.setInt(6, commissionBp); ps.setString(7, signedName)
                ps.setString(8, ip); ps.setString(9, agent?.take(300))
                ps.executeUpdate()
            }
            c.commit()
            true
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    /** Texte exact du contrat signé (le plus récent). */
    fun signedContractText(id: UUID): String? = withConn { c ->
        c.prepareStatement("SELECT contract_text FROM contract_signatures WHERE partner_id = ? ORDER BY signed_at DESC LIMIT 1").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun listPublishers(): List<AdminPublisherDto> = withConn { c ->
        c.prepareStatement(
            "SELECT $pubCols, " +
                "(SELECT count(*) FROM games g WHERE g.partner_id = partners.id) AS gc, " +
                "(SELECT count(*) FROM redeem_codes r WHERE r.partner_id = partners.id) AS cc, " +
                "(SELECT count(*) FROM redeem_codes r WHERE r.partner_id = partners.id AND r.redeemed_by IS NOT NULL) AS cr " +
                "FROM partners WHERE email IS NOT NULL ORDER BY created_at DESC"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                val out = ArrayList<AdminPublisherDto>()
                while (rs.next()) out.add(AdminPublisherDto(rs.toPublisher(), rs.getInt("gc"), rs.getInt("cc"), rs.getInt("cr")))
                out
            }
        }
    }

    /** (éditeur propriétaire, statut de validation) d'un jeu, ou null s'il n'existe pas. */
    fun ownerOfGame(gameId: String): Pair<UUID?, String>? = withConn { c ->
        c.prepareStatement("SELECT partner_id, review_status FROM games WHERE id = ?").use { ps ->
            ps.setString(1, gameId)
            ps.executeQuery().use { rs ->
                if (rs.next()) (rs.getObject(1) as UUID?) to rs.getString(2) else null
            }
        }
    }

    fun saveGameForPartner(partnerId: UUID, g: GameDto) {
        upsertGame(g)
        withConn { c ->
            c.prepareStatement("UPDATE games SET partner_id = ?, review_status = 'draft', review_note = NULL WHERE id = ?").use { ps ->
                ps.setObject(1, partnerId); ps.setString(2, g.id); ps.executeUpdate()
            }
        }
    }

    fun listPartnerGames(partnerId: UUID): List<PublisherGameDto> = withConn { c ->
        c.prepareStatement(
            "SELECT $GAME_COLS, review_status, review_note, " +
                "(SELECT count(*) FROM redeem_codes r WHERE r.game_id = games.id AND r.partner_id = games.partner_id) AS cc, " +
                "(SELECT count(*) FROM redeem_codes r WHERE r.game_id = games.id AND r.partner_id = games.partner_id AND r.redeemed_by IS NOT NULL) AS cr " +
                "FROM games WHERE partner_id = ? ORDER BY title"
        ).use { ps ->
            ps.setObject(1, partnerId)
            ps.executeQuery().use { rs ->
                val out = ArrayList<PublisherGameDto>()
                while (rs.next()) out.add(PublisherGameDto(rs.toGame(), rs.getString("review_status"), rs.getString("review_note"), rs.getInt("cc"), rs.getInt("cr")))
                out
            }
        }
    }

    fun submitGame(partnerId: UUID, gameId: String): Boolean = updateOne(
        "UPDATE games SET review_status = 'pending', review_note = NULL " +
            "WHERE id = ? AND partner_id = ? AND review_status IN ('draft','rejected')"
    ) { it.setString(1, gameId); it.setObject(2, partnerId) }

    fun reviewGame(gameId: String, approve: Boolean, note: String?): Boolean = updateOne(
        "UPDATE games SET review_status = ?, review_note = ?, published = ?, updated_at = now() " +
            "WHERE id = ? AND review_status = 'pending'"
    ) {
        it.setString(1, if (approve) "approved" else "rejected")
        it.setString(2, note?.take(500))
        it.setBoolean(3, approve)
        it.setString(4, gameId)
    }

    fun pendingReviews(): List<AdminReviewItem> = withConn { c ->
        c.prepareStatement(
            "SELECT $GAME_COLS, review_status, review_note, (SELECT name FROM partners p WHERE p.id = games.partner_id) AS pname " +
                "FROM games WHERE review_status = 'pending' ORDER BY updated_at"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                val out = ArrayList<AdminReviewItem>()
                while (rs.next()) out.add(AdminReviewItem(rs.toGame(), rs.getString("pname"), rs.getString("review_status"), rs.getString("review_note")))
                out
            }
        }
    }

    fun salesLines(partnerId: UUID): List<SalesLine> = withConn { c ->
        c.prepareStatement(
            "SELECT g.id, g.title, o.currency, (o.provider = 'sandbox') AS test, count(*), COALESCE(sum(o.amount_cents),0) " +
                "FROM orders o JOIN games g ON g.id = o.game_id " +
                "WHERE g.partner_id = ? AND o.status = 'paid' " +
                "GROUP BY g.id, g.title, o.currency, (o.provider = 'sandbox') ORDER BY g.title"
        ).use { ps ->
            ps.setObject(1, partnerId)
            ps.executeQuery().use { rs ->
                val out = ArrayList<SalesLine>()
                while (rs.next()) out.add(SalesLine(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5), rs.getLong(6)))
                out
            }
        }
    }

    fun payoutsOf(partnerId: UUID): List<PayoutDto> = withConn { c ->
        c.prepareStatement("SELECT id, amount_cents, currency, note, created_at FROM payouts WHERE partner_id = ? ORDER BY created_at DESC").use { ps ->
            ps.setObject(1, partnerId)
            ps.executeQuery().use { rs ->
                val out = ArrayList<PayoutDto>()
                while (rs.next()) out.add(PayoutDto(rs.getObject(1).toString(), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant().toString()))
                out
            }
        }
    }

    fun addPayout(partnerId: UUID, amountCents: Long, currency: String, note: String?) = withConn { c ->
        c.prepareStatement("INSERT INTO payouts (id,partner_id,amount_cents,currency,note) VALUES (?,?,?,?,?)").use { ps ->
            ps.setObject(1, UUID.randomUUID()); ps.setObject(2, partnerId); ps.setLong(3, amountCents)
            ps.setString(4, currency); ps.setString(5, note?.take(300))
            ps.executeUpdate()
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

    // ---------- Codes de téléchargement et bibliothèque ----------

    fun createCodes(gameId: String, codeHashes: List<String>, expiresAt: Instant?) = withConn { c ->
        c.autoCommit = false
        try {
            c.prepareStatement("INSERT INTO redeem_codes (id,code_hash,game_id,expires_at) VALUES (?,?,?,?)").use { ps ->
                for (h in codeHashes) {
                    ps.setObject(1, UUID.randomUUID())
                    ps.setString(2, h)
                    ps.setString(3, gameId)
                    ps.setTimestamp(4, expiresAt?.let { Timestamp.from(it) })
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
    }

    // ---------- Partenaires (éditeurs et revendeurs) ----------

    fun createPartner(name: String, kind: String, keyHash: String, gameIds: List<String>): UUID = withConn { c ->
        val id = UUID.randomUUID()
        c.autoCommit = false
        try {
            c.prepareStatement("INSERT INTO partners (id,name,kind,key_hash) VALUES (?,?,?,?)").use { ps ->
                ps.setObject(1, id); ps.setString(2, name); ps.setString(3, kind); ps.setString(4, keyHash)
                ps.executeUpdate()
            }
            setPartnerGames(c, id, gameIds)
            c.commit()
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
        id
    }

    private fun setPartnerGames(c: Connection, partnerId: UUID, gameIds: List<String>) {
        c.prepareStatement("DELETE FROM partner_games WHERE partner_id = ?").use { ps ->
            ps.setObject(1, partnerId); ps.executeUpdate()
        }
        c.prepareStatement("INSERT INTO partner_games (partner_id, game_id) VALUES (?,?) ON CONFLICT DO NOTHING").use { ps ->
            for (g in gameIds.distinct()) { ps.setObject(1, partnerId); ps.setString(2, g); ps.addBatch() }
            ps.executeBatch()
        }
    }

    fun gamesExist(ids: List<String>): Boolean = withConn { c ->
        c.prepareStatement("SELECT count(*) FROM games WHERE id = ANY(?)").use { ps ->
            ps.setArray(1, c.createArrayOf("text", ids.distinct().toTypedArray()))
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) == ids.distinct().size }
        }
    }

    fun listPartners(): List<PartnerDto> = withConn { c ->
        val games = HashMap<UUID, MutableList<String>>()
        c.createStatement().use { st ->
            st.executeQuery("SELECT partner_id, game_id FROM partner_games ORDER BY game_id").use { rs ->
                while (rs.next()) games.getOrPut(rs.getObject(1) as UUID) { mutableListOf() }.add(rs.getString(2))
            }
        }
        val out = ArrayList<PartnerDto>()
        c.createStatement().use { st ->
            st.executeQuery(
                "SELECT p.id,p.name,p.kind,p.active,p.created_at, count(r.id), count(r.redeemed_by) " +
                    "FROM partners p LEFT JOIN redeem_codes r ON r.partner_id = p.id " +
                    "GROUP BY p.id ORDER BY p.created_at DESC"
            ).use { rs ->
                while (rs.next()) {
                    val id = rs.getObject(1) as UUID
                    out.add(
                        PartnerDto(
                            id.toString(), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                            games[id] ?: emptyList(), rs.getTimestamp(5).toInstant().toString(),
                            rs.getInt(6), rs.getInt(7)
                        )
                    )
                }
            }
        }
        out
    }

    fun getPartner(id: UUID): PartnerDto? = listPartners().firstOrNull { it.id == id.toString() }

    fun updatePartner(id: UUID, active: Boolean?, gameIds: List<String>?): Boolean = withConn { c ->
        c.autoCommit = false
        try {
            var found = true
            if (active != null) {
                c.prepareStatement("UPDATE partners SET active = ? WHERE id = ?").use { ps ->
                    ps.setBoolean(1, active); ps.setObject(2, id)
                    found = ps.executeUpdate() > 0
                }
            } else {
                c.prepareStatement("SELECT 1 FROM partners WHERE id = ?").use { ps ->
                    ps.setObject(1, id); ps.executeQuery().use { found = it.next() }
                }
            }
            if (found && gameIds != null) setPartnerGames(c, id, gameIds)
            c.commit()
            found
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    fun rotatePartnerKey(id: UUID, keyHash: String): Boolean = withConn { c ->
        c.prepareStatement("UPDATE partners SET key_hash = ? WHERE id = ?").use { ps ->
            ps.setString(1, keyHash); ps.setObject(2, id); ps.executeUpdate() > 0
        }
    }

    fun findPartnerByKey(keyHash: String): PartnerRow? = withConn { c ->
        c.prepareStatement("SELECT id,name,kind,active FROM partners WHERE key_hash = ?").use { ps ->
            ps.setString(1, keyHash)
            ps.executeQuery().use { rs ->
                if (rs.next()) PartnerRow(rs.getObject(1) as UUID, rs.getString(2), rs.getString(3), rs.getBoolean(4)) else null
            }
        }
    }

    fun partnerOwnsGame(partnerId: UUID, gameId: String): Boolean = withConn { c ->
        c.prepareStatement("SELECT 1 FROM partner_games WHERE partner_id = ? AND game_id = ?").use { ps ->
            ps.setObject(1, partnerId); ps.setString(2, gameId)
            ps.executeQuery().use { it.next() }
        }
    }

    fun partnerGameStats(partnerId: UUID): List<PartnerGameStats> = withConn { c ->
        c.prepareStatement(
            "SELECT g.id, g.title, count(r.id), count(r.redeemed_by) FROM partner_games pg " +
                "JOIN games g ON g.id = pg.game_id " +
                "LEFT JOIN redeem_codes r ON r.game_id = g.id AND r.partner_id = pg.partner_id " +
                "WHERE pg.partner_id = ? GROUP BY g.id, g.title ORDER BY g.title"
        ).use { ps ->
            ps.setObject(1, partnerId)
            ps.executeQuery().use { rs ->
                val out = ArrayList<PartnerGameStats>()
                while (rs.next()) out.add(PartnerGameStats(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)))
                out
            }
        }
    }

    /** Crée un lot de codes pour un partenaire. Renvoie false si cette référence de lot existe déjà. */
    fun createPartnerCodes(
        partnerId: UUID, gameId: String, reference: String, codeHashes: List<String>, expiresAt: Instant?
    ): Boolean = withConn { c ->
        c.autoCommit = false
        try {
            val inserted = c.prepareStatement(
                "INSERT INTO code_batches (partner_id,reference,game_id,quantity) VALUES (?,?,?,?) ON CONFLICT DO NOTHING"
            ).use { ps ->
                ps.setObject(1, partnerId); ps.setString(2, reference); ps.setString(3, gameId); ps.setInt(4, codeHashes.size)
                ps.executeUpdate()
            }
            if (inserted == 0) {
                c.rollback()
                return@withConn false
            }
            c.prepareStatement("INSERT INTO redeem_codes (id,code_hash,game_id,expires_at,partner_id) VALUES (?,?,?,?,?)").use { ps ->
                for (h in codeHashes) {
                    ps.setObject(1, UUID.randomUUID()); ps.setString(2, h); ps.setString(3, gameId)
                    ps.setTimestamp(4, expiresAt?.let { Timestamp.from(it) }); ps.setObject(5, partnerId)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()
            true
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    fun codeStats(gameId: String): CodeStats = withConn { c ->
        c.prepareStatement("SELECT count(*), count(redeemed_by) FROM redeem_codes WHERE game_id = ?").use { ps ->
            ps.setString(1, gameId)
            ps.executeQuery().use { rs -> rs.next(); CodeStats(rs.getInt(1), rs.getInt(2)) }
        }
    }

    /**
     * Utilise un code : ajoute le jeu à la bibliothèque du joueur. Renvoie null si le code est invalide,
     * expiré ou déjà utilisé. Lève 409 si le joueur possède déjà ce jeu (le code n'est alors pas consommé).
     */
    fun redeemCode(userId: UUID, codeHash: String): GameDto? {
        val gameId: String = withConn { c ->
            c.autoCommit = false
            try {
                val row = c.prepareStatement(
                    "SELECT id, game_id FROM redeem_codes WHERE code_hash = ? AND redeemed_by IS NULL " +
                        "AND (expires_at IS NULL OR expires_at > now()) FOR UPDATE"
                ).use { ps ->
                    ps.setString(1, codeHash)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getObject("id", UUID::class.java) to rs.getString("game_id") else null
                    }
                }
                if (row == null) {
                    c.rollback()
                    return@withConn ""
                }
                val owned = c.prepareStatement("SELECT 1 FROM user_games WHERE user_id = ? AND game_id = ?").use { ps ->
                    ps.setObject(1, userId)
                    ps.setString(2, row.second)
                    ps.executeQuery().use { it.next() }
                }
                if (owned) {
                    c.rollback()
                    throw ApiException(io.ktor.http.HttpStatusCode.Conflict, "already_owned", "Tu possèdes déjà ce jeu.")
                }
                c.prepareStatement("UPDATE redeem_codes SET redeemed_by = ?, redeemed_at = now() WHERE id = ?").use { ps ->
                    ps.setObject(1, userId)
                    ps.setObject(2, row.first)
                    ps.executeUpdate()
                }
                c.prepareStatement("INSERT INTO user_games (user_id, game_id, source) VALUES (?, ?, 'code') ON CONFLICT DO NOTHING").use { ps ->
                    ps.setObject(1, userId)
                    ps.setString(2, row.second)
                    ps.executeUpdate()
                }
                c.commit()
                row.second
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        }
        if (gameId.isEmpty()) return null
        return getGame(gameId, includeUnpublished = true)
    }

    fun ownedGames(userId: UUID): List<GameDto> = withConn { c ->
        c.prepareStatement(
            "SELECT $GAME_COLS FROM games WHERE published = TRUE AND id IN (SELECT game_id FROM user_games WHERE user_id = ?) ORDER BY title"
        ).use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                val list = ArrayList<GameDto>()
                while (rs.next()) list += rs.toGame()
                list
            }
        }
    }

    // ---------- Commandes et paiements ----------

    private fun ResultSet.toOrder() = OrderDto(
        id = getObject("id", UUID::class.java).toString(),
        gameId = getString("game_id"),
        gameTitle = getString("game_title"),
        amountCents = getInt("amount_cents"),
        currency = getString("currency"),
        provider = getString("provider"),
        status = getString("status"),
        createdAt = getTimestamp("created_at").toInstant().toString(),
        paidAt = getTimestamp("paid_at")?.toInstant()?.toString(),
        sandbox = getString("provider") == "sandbox"
    )

    private val orderCols = "id,game_id,game_title,amount_cents,currency,provider,status,created_at,paid_at"

    fun ownsGame(userId: UUID, gameId: String): Boolean = withConn { c ->
        c.prepareStatement("SELECT 1 FROM user_games WHERE user_id = ? AND game_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, gameId)
            ps.executeQuery().use { it.next() }
        }
    }

    /** Ajoute un jeu à la bibliothèque (jeu gratuit, achat confirmé…). */
    fun grantGame(userId: UUID, gameId: String, source: String) = withConn { c ->
        c.prepareStatement(
            "INSERT INTO user_games (user_id, game_id, source) SELECT ?, id, ? FROM games WHERE id = ? ON CONFLICT DO NOTHING"
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, source)
            ps.setString(3, gameId)
            ps.executeUpdate()
        }
    }

    /** Crée une commande en attente, ou renvoie celle déjà en attente pour ce joueur, ce jeu et ce fournisseur. */
    fun createOrder(userId: UUID, game: GameDto, provider: String): OrderDto = withConn { c ->
        val existing = c.prepareStatement(
            "SELECT $orderCols FROM orders WHERE user_id = ? AND game_id = ? AND provider = ? AND status = 'pending' " +
                "ORDER BY created_at DESC LIMIT 1"
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, game.id)
            ps.setString(3, provider)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toOrder() else null }
        }
        if (existing != null) return@withConn existing
        val id = UUID.randomUUID()
        c.prepareStatement(
            "INSERT INTO orders (id,user_id,game_id,game_title,amount_cents,currency,provider) VALUES (?,?,?,?,?,?,?)"
        ).use { ps ->
            ps.setObject(1, id)
            ps.setObject(2, userId)
            ps.setString(3, game.id)
            ps.setString(4, game.title)
            ps.setInt(5, game.priceCents ?: 0)
            ps.setString(6, game.currency)
            ps.setString(7, provider)
            ps.executeUpdate()
        }
        c.prepareStatement("SELECT $orderCols FROM orders WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> rs.next(); rs.toOrder() }
        }
    }

    fun getOrder(userId: UUID, orderId: UUID): OrderDto? = withConn { c ->
        c.prepareStatement("SELECT $orderCols FROM orders WHERE id = ? AND user_id = ?").use { ps ->
            ps.setObject(1, orderId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toOrder() else null }
        }
    }

    fun getOrderAny(orderId: UUID): OrderDto? = withConn { c ->
        c.prepareStatement("SELECT $orderCols FROM orders WHERE id = ?").use { ps ->
            ps.setObject(1, orderId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toOrder() else null }
        }
    }

    fun listOrders(userId: UUID): List<OrderDto> = withConn { c ->
        c.prepareStatement("SELECT $orderCols FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 100").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                val list = ArrayList<OrderDto>()
                while (rs.next()) list += rs.toOrder()
                list
            }
        }
    }

    fun adminListOrders(): List<AdminOrderDto> = withConn { c ->
        c.prepareStatement(
            "SELECT o.id,o.game_id,o.game_title,o.amount_cents,o.currency,o.provider,o.status,o.created_at,o.paid_at,u.display_name " +
                "FROM orders o LEFT JOIN users u ON u.id = o.user_id ORDER BY o.created_at DESC LIMIT 100"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                val list = ArrayList<AdminOrderDto>()
                while (rs.next()) list += AdminOrderDto(rs.toOrder(), rs.getString("display_name"))
                list
            }
        }
    }

    /** Marque la commande payée et ajoute le jeu à la bibliothèque, en une seule transaction. Idempotent. */
    fun markOrderPaid(orderId: UUID): Boolean = withConn { c ->
        c.autoCommit = false
        try {
            val row = c.prepareStatement("SELECT user_id, game_id, status FROM orders WHERE id = ? FOR UPDATE").use { ps ->
                ps.setObject(1, orderId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) Triple(rs.getObject("user_id", UUID::class.java), rs.getString("game_id"), rs.getString("status")) else null
                }
            }
            if (row == null || row.third != "pending") {
                c.rollback()
                return@withConn false
            }
            c.prepareStatement("UPDATE orders SET status = 'paid', paid_at = now() WHERE id = ?").use { ps ->
                ps.setObject(1, orderId)
                ps.executeUpdate()
            }
            if (row.first != null) {
                c.prepareStatement(
                    "INSERT INTO user_games (user_id, game_id, source) SELECT ?, id, 'purchase' FROM games WHERE id = ? ON CONFLICT DO NOTHING"
                ).use { ps ->
                    ps.setObject(1, row.first)
                    ps.setString(2, row.second)
                    ps.executeUpdate()
                }
            }
            c.commit()
            true
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    fun markOrderFailed(orderId: UUID): Boolean = withConn { c ->
        c.prepareStatement("UPDATE orders SET status = 'failed' WHERE id = ? AND status = 'pending'").use { ps ->
            ps.setObject(1, orderId)
            ps.executeUpdate() > 0
        }
    }
}
