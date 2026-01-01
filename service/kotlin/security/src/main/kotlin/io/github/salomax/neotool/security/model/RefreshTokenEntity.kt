package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * Entity representing a refresh token with rotation support.
 *
 * Stores refresh tokens as SHA-256 hashes (never plaintext) and supports:
 * - Token rotation (one-time use)
 * - Token families (grouping tokens in rotation chain)
 * - Reuse detection (theft detection)
 * - Revocation support
 */
@Entity
@Table(name = "refresh_tokens", schema = "security")
open class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    open val userId: UUID,
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    open val user: UserEntity? = null,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    open val tokenHash: String,
    @Column(name = "family_id", nullable = false, columnDefinition = "uuid")
    open val familyId: UUID,
    @Column(name = "issued_at", nullable = false)
    open val issuedAt: Instant = Instant.now(),
    @Column(name = "expires_at", nullable = false)
    open val expiresAt: Instant,
    @Column(name = "revoked_at")
    open var revokedAt: Instant? = null,
    @ManyToOne
    @JoinColumn(name = "replaced_by")
    open var replacedBy: RefreshTokenEntity? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    /**
     * Check if token is active (not revoked and not expired).
     */
    fun isActive(): Boolean {
        val now = Instant.now()
        return revokedAt == null && expiresAt.isAfter(now)
    }

    /**
     * Check if token has been replaced (used in rotation).
     */
    fun isReplaced(): Boolean = replacedBy != null
}

