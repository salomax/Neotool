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
@Table(name = "product_alias", schema = "banking")
open class ProductAliasEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "product_variant_id", nullable = false)
    open var productVariantId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "raw_description", nullable = false)
    open var rawDescription: String,
    @Column(name = "source", nullable = false)
    open var source: String,
    @Column(name = "confidence", precision = 3, scale = 2)
    open var confidence: BigDecimal? = null,
    @Column(name = "confirmed", nullable = false)
    open var confirmed: Boolean = false,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
