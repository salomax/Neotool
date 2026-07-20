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
@Table(name = "oauth2_authorization_codes", schema = "security")
open class OAuth2AuthorizationCodeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    open var codeHash: String,
    @Column(name = "authorization_request_id", nullable = false, columnDefinition = "uuid")
    open var authorizationRequestId: UUID,
    @ManyToOne
    @JoinColumn(name = "authorization_request_id", insertable = false, updatable = false)
    open val authorizationRequest: OAuth2AuthorizationRequestEntity? = null,
    @Column(name = "issued_access_token_jti", length = 64)
    open var issuedAccessTokenJti: String? = null,
    @Column(name = "issued_refresh_token_family_id", columnDefinition = "uuid")
    open var issuedRefreshTokenFamilyId: UUID? = null,
    @Column(name = "consumed_at")
    open var consumedAt: Instant? = null,
    @Column(name = "expires_at", nullable = false)
    open var expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id)
