package io.github.salomax.neotool.mcp.security

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
@Introspected
data class ConnectionRevocationId(
    @Column(name = "user_id")
    val userId: UUID,
    @Column(name = "client_id")
    val clientId: String,
    @Column(name = "account_id")
    val accountId: UUID,
) : Serializable

@Entity
@MappedEntity("mcp.connection_revocations")
@Table(name = "connection_revocations", schema = "mcp")
@Introspected
data class ConnectionRevocationEntity(
    @EmbeddedId
    val id: ConnectionRevocationId,
    @MappedProperty("revoked_at")
    val revokedAt: Instant,
    @MappedProperty("revoked_until")
    val revokedUntil: Instant? = null,
    val reason: String? = null,
)
