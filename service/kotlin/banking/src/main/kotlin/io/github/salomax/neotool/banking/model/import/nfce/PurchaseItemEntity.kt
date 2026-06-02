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
@Table(name = "purchase_items", schema = "banking")
open class PurchaseItemEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "transaction_id", nullable = false)
    open var transactionId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "product_variant_id")
    open var productVariantId: UUID? = null,
    @Column(name = "sequence", nullable = false)
    open var sequence: Int,
    @Column(name = "raw_description", nullable = false)
    open var rawDescription: String,
    @Column(name = "ncm_code")
    open var ncmCode: String? = null,
    @Column(name = "ean")
    open var ean: String? = null,
    @Column(name = "issuer_product_code")
    open var issuerProductCode: String? = null,
    @Column(name = "quantity", nullable = false, precision = 12, scale = 4)
    open var quantity: BigDecimal,
    @Column(name = "unit", nullable = false)
    open var unit: String,
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    open var unitPrice: BigDecimal,
    @Column(name = "total_price", nullable = false, precision = 18, scale = 4)
    open var totalPrice: BigDecimal,
    @Column(name = "discount", precision = 18, scale = 4)
    open var discount: BigDecimal? = null,
    @Column(name = "effective_price", nullable = false, precision = 18, scale = 4)
    open var effectivePrice: BigDecimal,
    @Column(name = "tax_amount", precision = 18, scale = 4)
    open var taxAmount: BigDecimal? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
