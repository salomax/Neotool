package io.github.salomax.neotool.banking.model.wishlist

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "wishlist_item", schema = "banking")
open class WishlistItemEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(nullable = false)
    open var name: String,
    @Column(name = "estimated_price", precision = 19, scale = 4)
    open var estimatedPrice: BigDecimal? = null,
    @Column(name = "currency_code", nullable = false, length = 3)
    open var currencyCode: String = "BRL",
    @Column(name = "category_id")
    open var categoryId: UUID? = null,
    @Column(nullable = false, length = 10)
    open var priority: String,
    @Column
    open var notes: String? = null,
    @Column(name = "target_date")
    open var targetDate: LocalDate? = null,
    @Column(name = "product_url")
    open var productUrl: String? = null,
    @Column(name = "product_image_url")
    open var productImageUrl: String? = null,
    @Column(nullable = false, length = 20)
    open var status: String = "ACTIVE",
    @Column(name = "sort_order", nullable = false)
    open var sortOrder: Int = 0,
    @Column(name = "purchased_at")
    open var purchasedAt: LocalDate? = null,
    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
