package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.investment.InvestmentTransactionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA repository for banking.investment_transactions (entity layer).
 */
@Repository
interface InvestmentTransactionEntityRepository : JpaRepository<InvestmentTransactionEntity, UUID> {
    fun findByHoldingIdAndAccountIdAndDeletedAtIsNullOrderByTradeDateDesc(
        holdingId: UUID,
        accountId: UUID,
    ): List<InvestmentTransactionEntity>

    fun findByAccountIdAndDeletedAtIsNullOrderByTradeDateDesc(
        accountId: UUID,
    ): List<InvestmentTransactionEntity>

    fun findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByTradeDateDesc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<InvestmentTransactionEntity>

    @Query(
        """
        SELECT * FROM banking.investment_transactions
         WHERE account_id = :accountId
           AND type = :type
           AND trade_date BETWEEN :from AND :to
           AND deleted_at IS NULL
         ORDER BY trade_date
        """,
        nativeQuery = true,
    )
    fun findByTypeAndDateRange(
        accountId: UUID,
        type: String,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentTransactionEntity>

    fun findByIdAndAccountIdAndDeletedAtIsNull(
        id: UUID,
        accountId: UUID,
    ): InvestmentTransactionEntity?

    fun existsByFinancialAccountIdAndProviderTransactionIdAndDeletedAtIsNull(
        financialAccountId: UUID,
        providerTransactionId: String,
    ): Boolean

    @Query(
        """
        UPDATE banking.investment_transactions
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
