package io.github.salomax.neotool.banking.model.transaction

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for banking.monthly_balance_snapshots.
 *
 * PK is composite: (financial_account_id, year, month).
 * All writes go through native UPSERT queries in [MonthlyBalanceSnapshotEntityRepository]
 * to guarantee INSERT ON CONFLICT DO UPDATE semantics.
 */
@Entity
@Table(name = "monthly_balance_snapshots", schema = "banking")
open class MonthlyBalanceSnapshotEntity(

    @EmbeddedId
    open var id: MonthlyBalanceSnapshotId,

    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,

    @Column(name = "opening_balance", nullable = false, precision = 18, scale = 4)
    open var openingBalance: BigDecimal,

    @Column(name = "closing_balance", nullable = false, precision = 18, scale = 4)
    open var closingBalance: BigDecimal,

    @Column(name = "total_income", nullable = false, precision = 18, scale = 4)
    open var totalIncome: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_expenses", nullable = false, precision = 18, scale = 4)
    open var totalExpenses: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_dirty", nullable = false)
    open var isDirty: Boolean = false,

    @Column(name = "computed_at")
    open var computedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
