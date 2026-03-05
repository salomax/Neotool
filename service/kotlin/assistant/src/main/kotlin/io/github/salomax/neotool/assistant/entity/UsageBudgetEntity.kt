package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.usage_budgets.
 */
@Entity
@Table(
    name = "usage_budgets",
    schema = "assistant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_usage_budgets_scope_scope_id_period",
            columnNames = ["scope", "scope_id", "period"],
        ),
    ],
)
open class UsageBudgetEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var scope: BudgetScope,
    @Column(name = "scope_id", nullable = false, columnDefinition = "uuid")
    open var scopeId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var period: BudgetPeriod,
    @Column(name = "limit_amount", nullable = false, precision = 18, scale = 6)
    open var limitAmount: BigDecimal,
    @Column(name = "current_usage", nullable = false, precision = 18, scale = 6)
    open var currentUsage: BigDecimal = BigDecimal.ZERO,
    @Column(name = "alert_threshold", nullable = false, precision = 5, scale = 2)
    open var alertThreshold: BigDecimal = BigDecimal("0.80"),
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Version
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
