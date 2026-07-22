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

@Entity
@Table(name = "oauth2_consents", schema = "security")
open class OAuth2ConsentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    open var userId: UUID,
    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    open val user: UserEntity? = null,
    @Column(name = "client_id", nullable = false)
    open var clientId: String,
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    open var accountId: UUID,
    @Column(name = "granted_scopes", nullable = false, columnDefinition = "TEXT")
    open var grantedScopes: String,
    @Column(name = "first_granted_at", nullable = false)
    open var firstGrantedAt: Instant = Instant.now(),
    @Column(name = "last_granted_at", nullable = false)
    open var lastGrantedAt: Instant = Instant.now(),
    @Column(name = "revoked_at")
    open var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id)
