package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Entity representing service credentials (hashed client secrets).
 * Stores Argon2id hashes of client secrets for service principals.
 */
@Entity
@Table(name = "service_credentials", schema = "security")
open class ServiceCredentialEntity(
    @Id
    @Column(name = "principal_id", columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "credential_hash", nullable = false)
    open var credentialHash: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)

