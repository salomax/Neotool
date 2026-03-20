package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for banking.merchants.
 * logo is NOT stored here — resolved via merchant_logo_cache keyed by [domain].
 */
@Entity
@Table(name = "merchants", schema = "banking")
open class MerchantEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(nullable = false, length = 200)
    open var name: String,
    @Column(length = 200)
    open var domain: String? = null,
    @Column(length = 100)
    open var sector: String? = null,
    @Column(name = "is_system", nullable = false)
    open var isSystem: Boolean = false,
    @Column(name = "account_id")
    open var accountId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
