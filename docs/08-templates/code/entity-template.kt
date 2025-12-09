package io.github.salomax.neotool.{module}.entity

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.{module}.domain.{DomainName}
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for {EntityName}.
 * 
 * Replace:
 * - {module} with your module name (app, security, assistant, etc.)
 * - {EntityName} with your entity name (e.g., Product, Customer)
 * - {DomainName} with your domain object name (e.g., Product, Customer)
 * - {table_name} with your database table name (snake_case, e.g., products, customers)
 * - Add/remove fields as needed
 */
@Entity
@Table(name = "{table_name}")
open class {EntityName}Entity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID?,
    @Column(nullable = false)
    open var name: String,
    // Add your fields here
    // Example: @Column(nullable = false, unique = true)
    // open var email: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    /**
     * Convert entity to domain object.
     * Map all fields from entity to domain.
     */
    fun toDomain(): {DomainName} {
        return {DomainName}(
            id = this.id,
            name = this.name,
            // Map all fields here
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
