package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshot
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.model.transaction.MonthlyBalanceSnapshotMapper
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.Instant
import java.util.UUID

/**
 * Default implementation of [MonthlyBalanceSnapshotRepository] using JPA + native UPSERT.
 */
@Singleton
open class DefaultMonthlyBalanceSnapshotRepository(
    private val entityRepository: MonthlyBalanceSnapshotEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : MonthlyBalanceSnapshotRepository {
    override fun findByYearMonth(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthlyBalanceSnapshot? =
        entityRepository.findByKey(financialAccountId, year, month)
            ?.let(MonthlyBalanceSnapshotMapper::toDomain)

    override fun findLatestCleanBefore(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthlyBalanceSnapshot? =
        entityRepository.findLatestCleanBefore(financialAccountId, year, month)
            .firstOrNull()
            ?.let(MonthlyBalanceSnapshotMapper::toDomain)

    override fun findAllDirty(financialAccountId: UUID): List<MonthlyBalanceSnapshot> =
        entityRepository.findAllDirty(financialAccountId)
            .map(MonthlyBalanceSnapshotMapper::toDomain)

    override fun findFinancialAccountIdsWithDirtySnapshots(): List<UUID> =
        entityRepository.findFinancialAccountIdsWithDirtySnapshots()

    override fun upsert(snapshot: MonthlyBalanceSnapshot): MonthlyBalanceSnapshot {
        entityRepository.upsert(
            financialAccountId = snapshot.financialAccountId,
            accountId = snapshot.accountId,
            year = snapshot.year,
            month = snapshot.month,
            openingBalance = snapshot.openingBalance,
            closingBalance = snapshot.closingBalance,
            totalIncome = snapshot.totalIncome,
            totalExpenses = snapshot.totalExpenses,
            isDirty = snapshot.isDirty,
            computedAt = snapshot.computedAt ?: Instant.now(),
        )
        // Native UPSERT bypasses first-level cache. Clear to avoid stale reads by key.
        entityManager.clear()
        // Re-read to get DB-stamped timestamps
        return entityRepository.findByKey(snapshot.financialAccountId, snapshot.year, snapshot.month)!!
            .let(MonthlyBalanceSnapshotMapper::toDomain)
    }

    override fun markDirtyFromMonth(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ) {
        entityRepository.markDirtyFromMonth(financialAccountId, year, month)
        // Native UPDATE bypasses first-level cache. Clear so subsequent reads observe dirty flags.
        entityManager.clear()
    }

    override fun findByRange(
        financialAccountId: UUID,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<MonthlyBalanceSnapshot> =
        entityRepository.findByRange(financialAccountId, startYear, startMonth, endYear, endMonth)
            .map(MonthlyBalanceSnapshotMapper::toDomain)

    override fun findByRangeForAccount(
        accountId: UUID,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<MonthlyBalanceSnapshot> =
        entityRepository.findByRangeForAccount(accountId, startYear, startMonth, endYear, endMonth)
            .map(MonthlyBalanceSnapshotMapper::toDomain)
}
