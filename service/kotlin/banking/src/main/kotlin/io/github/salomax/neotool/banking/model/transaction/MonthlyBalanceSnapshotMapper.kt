package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshot

object MonthlyBalanceSnapshotMapper {

    fun toDomain(entity: MonthlyBalanceSnapshotEntity): MonthlyBalanceSnapshot =
        MonthlyBalanceSnapshot(
            financialAccountId = entity.id.financialAccountId,
            accountId = entity.accountId,
            year = entity.id.year,
            month = entity.id.month,
            openingBalance = entity.openingBalance,
            closingBalance = entity.closingBalance,
            totalIncome = entity.totalIncome,
            totalExpenses = entity.totalExpenses,
            isDirty = entity.isDirty,
            computedAt = entity.computedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: MonthlyBalanceSnapshot): MonthlyBalanceSnapshotEntity =
        MonthlyBalanceSnapshotEntity(
            id = MonthlyBalanceSnapshotId(
                financialAccountId = domain.financialAccountId,
                year = domain.year,
                month = domain.month,
            ),
            accountId = domain.accountId,
            openingBalance = domain.openingBalance,
            closingBalance = domain.closingBalance,
            totalIncome = domain.totalIncome,
            totalExpenses = domain.totalExpenses,
            isDirty = domain.isDirty,
            computedAt = domain.computedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
}
