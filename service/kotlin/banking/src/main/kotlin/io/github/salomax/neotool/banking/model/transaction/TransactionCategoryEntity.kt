package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for banking.transaction_categories.
 */
@Entity
@Table(name = "transaction_categories", schema = "banking")
open class TransactionCategoryEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id")
    open var accountId: UUID? = null,
    @Column(name = "parent_id")
    open var parentId: UUID? = null,
    @Column(nullable = false)
    open var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_to", nullable = false, length = 10)
    open var applicableTo: CategoryApplicableTo = CategoryApplicableTo.ANY,
    @Column
    open var icon: String? = null,
    @Column
    open var color: String? = null,
    @Column(name = "is_system", nullable = false)
    open var isSystem: Boolean = false,
    @Column(name = "sort_order", nullable = false)
    open var sortOrder: Int = 0,
    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
