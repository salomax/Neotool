package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.model.investment.HoldingEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
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
}
