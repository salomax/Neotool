package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Embedded
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
 * JPA entity for banking.transaction_rules.
 */
@Entity
@Table(name = "transaction_rules", schema = "banking")
open class CategoryRuleEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id")
    open var accountId: UUID? = null,
    @Column(name = "category_id", nullable = true)
    open var categoryId: UUID? = null,
    @Column(name = "merchant_id", nullable = true)
    open var merchantId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "type_override", length = 10)
    open var typeOverride: CategoryApplicableTo? = null,
    @Column(name = "description_override")
    open var descriptionOverride: String? = null,
    @Column(name = "tags_override_json")
    open var tagsOverrideJson: String? = null,
    @Column(nullable = false)
    open var pattern: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = false, length = 20)
    open var patternType: PatternType,
    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_to", nullable = false, length = 10)
    open var applicableTo: CategoryApplicableTo = CategoryApplicableTo.ANY,
    @Column(nullable = false)
    open var priority: Int = 100,
    @Column(name = "is_system", nullable = false)
    open var isSystem: Boolean = false,
    @Column(name = "is_implicit", nullable = false)
    open var isImplicit: Boolean = false,
    @Column(name = "match_count", nullable = false)
    open var matchCount: Int = 0,
    @Embedded
    open var origin: ChangeOriginEmbeddable = ChangeOriginEmbeddable(),
    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
