package io.github.salomax.neotool.security.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "email_verifications", schema = "security")
open class EmailVerificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    open val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    open val user: UserEntity,
    @Column(name = "token_hash", nullable = false)
    open val tokenHash: String,
    @Column(name = "attempts", nullable = false)
    open var attempts: Int = 0,
    @Column(name = "max_attempts", nullable = false)
    open val maxAttempts: Int = 5,
    @Column(name = "expires_at", nullable = false)
    open val expiresAt: Instant,
    @Column(name = "verified_at")
    open var verifiedAt: Instant? = null,
    @Column(name = "invalidated_at")
    open var invalidatedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    open val createdAt: Instant = Instant.now(),
    @Column(name = "resend_count", nullable = false)
    open var resendCount: Int = 0,
    @Column(name = "created_by_ip", length = 45)
    open val createdByIp: String? = null,
    @Column(name = "verified_from_ip", length = 45)
    open var verifiedFromIp: String? = null,
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun isVerified(): Boolean = verifiedAt != null

    fun isInvalidated(): Boolean = invalidatedAt != null

    fun canRetry(): Boolean = attempts < maxAttempts && !isExpired() && !isInvalidated()

    fun markVerified(ipAddress: String) {
        verifiedAt = Instant.now()
        verifiedFromIp = ipAddress
    }

    fun markInvalidated() {
        invalidatedAt = Instant.now()
    }

    fun incrementAttempts() {
        attempts++
    }
}
