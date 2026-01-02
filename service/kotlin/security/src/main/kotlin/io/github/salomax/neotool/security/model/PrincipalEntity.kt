package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.common.security.principal.PrincipalType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * Entity representing a principal (user or service).
 * Principals are the subjects that can be assigned permissions.
 */
@Entity
@Table(name = "principals", schema = "security")
open class PrincipalEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false)
    open var principalType: PrincipalType,
    @Column(name = "external_id", nullable = false)
    open var externalId: String,
    @Column(nullable = false)
    open var enabled: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): Principal =
        Principal(
            id = this.id,
            principalType = this.principalType,
            externalId = this.externalId,
            enabled = this.enabled,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
}

/**
 * Domain model for Principal.
 */
data class Principal(
    val id: UUID?,
    val principalType: PrincipalType,
    val externalId: String,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)
