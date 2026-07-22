package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class OAuth2AuthorizationRequestStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
}

@Entity
@Table(name = "oauth2_authorization_requests", schema = "security")
open class OAuth2AuthorizationRequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "client_id", nullable = false)
    open var clientId: String,
    @Column(name = "redirect_uri", nullable = false)
    open var redirectUri: String,
    @Column(name = "requested_scopes", nullable = false)
    open var requestedScopes: String,
    @Column(name = "granted_scopes")
    open var grantedScopes: String? = null,
    @Column(name = "code_challenge", nullable = false)
    open var codeChallenge: String,
    @Column(nullable = false)
    open var resource: String,
    @Column
    open var state: String? = null,
    @Column(name = "user_id", columnDefinition = "uuid")
    open var userId: UUID? = null,
    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    open val user: UserEntity? = null,
    @Column(name = "account_id", columnDefinition = "uuid")
    open var accountId: UUID? = null,
    @Column(name = "csrf_token_hash", length = 64)
    open var csrfTokenHash: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: OAuth2AuthorizationRequestStatus = OAuth2AuthorizationRequestStatus.PENDING,
    @Column(name = "expires_at", nullable = false)
    open var expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id)
