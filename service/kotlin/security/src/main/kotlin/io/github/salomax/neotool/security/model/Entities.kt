package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users", schema = "security")
open class UserEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    open val email: String,
    @Column(name = "display_name")
    open var displayName: String? = null,
    @Column(name = "password_hash")
    open var passwordHash: String? = null,
    @Column(name = "remember_me_token")
    open var rememberMeToken: String? = null,
    @Column(name = "password_reset_token")
    open var passwordResetToken: String? = null,
    @Column(name = "password_reset_expires_at")
    open var passwordResetExpiresAt: Instant? = null,
    @Column(name = "password_reset_used_at")
    open var passwordResetUsedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID>(id)

@Entity
@Table(name = "roles", schema = "security")
open class RoleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Int? = null,
    @Column(nullable = false, unique = true)
    open var name: String,
) : BaseEntity<Int?>(id)

@Entity
@Table(name = "permissions", schema = "security")
open class PermissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Int? = null,
    @Column(nullable = false, unique = true)
    open var name: String,
) : BaseEntity<Int?>(id)

@Entity
@Table(name = "password_reset_attempts", schema = "security")
open class PasswordResetAttemptEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    open val email: String,
    @Column(name = "attempt_count", nullable = false)
    open var attemptCount: Int = 1,
    @Column(name = "window_start", nullable = false)
    open var windowStart: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID>(id)
