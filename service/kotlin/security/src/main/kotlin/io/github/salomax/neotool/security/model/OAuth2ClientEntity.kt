package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class OAuth2ClientStatus {
    ACTIVE,
    DELETED,
}

@Entity
@Table(name = "oauth2_clients", schema = "security")
open class OAuth2ClientEntity(
    @Id
    @Column(name = "client_id", nullable = false, length = 255)
    override val id: String,
    @Column(name = "client_name")
    open var clientName: String? = null,
    @Column(name = "redirect_uris", nullable = false, columnDefinition = "TEXT")
    open var redirectUris: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    open var scopes: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    open var resources: String,
    @Column(name = "grant_types", nullable = false, columnDefinition = "TEXT")
    open var grantTypes: String,
    @Column(name = "response_types", nullable = false, columnDefinition = "TEXT")
    open var responseTypes: String,
    @Column(name = "token_endpoint_auth_method", nullable = false, length = 64)
    open var tokenEndpointAuthMethod: String,
    @Column(name = "logo_uri")
    open var logoUri: String? = null,
    @Column(name = "client_uri")
    open var clientUri: String? = null,
    @Column(name = "client_id_issued_at", nullable = false)
    open var clientIdIssuedAt: Instant,
    @Column(name = "last_used_at")
    open var lastUsedAt: Instant? = null,
    @Column(name = "created_from_ip")
    open var createdFromIp: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    open var status: OAuth2ClientStatus = OAuth2ClientStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<String>(id)
