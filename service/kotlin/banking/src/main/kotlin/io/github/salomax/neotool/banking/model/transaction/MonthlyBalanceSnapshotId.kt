package io.github.salomax.neotool.banking.model.transaction

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * Composite primary key for [MonthlyBalanceSnapshotEntity]:
 * (financial_account_id, year, month).
 */
@Embeddable
data class MonthlyBalanceSnapshotId(
    @Column(name = "financial_account_id", nullable = false)
    val financialAccountId: UUID,
    @Column(name = "year", nullable = false)
    val year: Int,
    @Column(name = "month", nullable = false)
    val month: Int,
) : Serializable
