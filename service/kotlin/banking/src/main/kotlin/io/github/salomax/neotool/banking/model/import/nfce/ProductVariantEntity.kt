package io.github.salomax.neotool.banking.model.import.nfce

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "product_variants", schema = "banking")
open class ProductVariantEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "product_id", nullable = false)
    open var productId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "name", nullable = false)
    open var name: String,
    @Column(name = "brand")
    open var brand: String? = null,
    @Column(name = "ean")
    open var ean: String? = null,
    @Column(name = "quantity_value", precision = 12, scale = 4)
    open var quantityValue: BigDecimal? = null,
    @Column(name = "quantity_unit")
    open var quantityUnit: String? = null,
    @Column(name = "normalized_quantity_value", precision = 12, scale = 4)
    open var normalizedQuantityValue: BigDecimal? = null,
    @Column(name = "normalized_quantity_unit")
    open var normalizedQuantityUnit: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
