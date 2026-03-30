package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.model.investment.HoldingEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA repository for banking.investment_holdings (entity layer).
 */
@Repository
interface HoldingEntityRepository : JpaRepository<HoldingEntity, UUID> {
    fun findByFinancialAccountIdAndAccountIdAndDeletedAtIsNull(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<HoldingEntity>

    fun findByAccountIdAndDeletedAtIsNull(accountId: UUID): List<HoldingEntity>

    fun findByIdAndAccountIdAndDeletedAtIsNull(
        id: UUID,
        accountId: UUID,
    ): HoldingEntity?

    fun findByFinancialAccountIdAndAssetIdAndDeletedAtIsNull(
        financialAccountId: UUID,
        assetId: UUID,
    ): HoldingEntity?

    fun findByFinancialAccountIdAndProviderInvestmentIdAndDeletedAtIsNull(
        financialAccountId: UUID,
        providerInvestmentId: String,
    ): HoldingEntity?

    @Query(
        """
        UPDATE banking.investment_holdings
           SET deleted_at = :deletedAt,
               updated_at = :deletedAt
         WHERE id = :id
           AND account_id = :accountId
           AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDelete(
        id: UUID,
        accountId: UUID,
        deletedAt: Instant,
    ): Int

    @Query(
        """
        SELECT h.* FROM banking.investment_holdings h
        INNER JOIN banking.financial_accounts fa ON fa.id = h.financial_account_id
        WHERE h.deleted_at IS NULL
          AND fa.deleted_at IS NULL
          AND fa.type = 'INVESTMENT'
          AND h.quantity > 0
          AND h.close_reason IS NULL
          AND h.maturity_date IS NOT NULL
          AND h.maturity_date <= :asOf
          AND h.source IN ('MANUAL', 'FILE_IMPORT')
        """,
        nativeQuery = true,
    )
    fun findMaturedOpenHoldingsForPendingScan(asOf: LocalDate): List<HoldingEntity>
}
