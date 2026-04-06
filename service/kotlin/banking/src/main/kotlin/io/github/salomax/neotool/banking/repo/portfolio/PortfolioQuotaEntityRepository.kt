package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioQuotaEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
interface PortfolioQuotaEntityRepository : JpaRepository<InvestmentPortfolioQuotaEntity, UUID> {
    @Query(
        """
        SELECT q FROM InvestmentPortfolioQuotaEntity q
        WHERE q.financialAccountId = :financialAccountId
          AND q.dimensionType = :dimensionType
          AND q.dimensionKey = :dimensionKey
          AND q.quotaDate <= :date
        ORDER BY q.quotaDate DESC
        """,
    )
    fun findLatestBefore(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String,
        date: LocalDate,
    ): List<InvestmentPortfolioQuotaEntity>

    @Query(
        """
        SELECT DISTINCT ON (dimension_type, dimension_key) *
        FROM banking.investment_portfolio_quota
        WHERE financial_account_id = :financialAccountId
          AND quota_date <= :date
        ORDER BY dimension_type, dimension_key, quota_date DESC
        """,
        nativeQuery = true,
    )
    fun findLatestBeforeAllDimensions(
        financialAccountId: UUID,
        date: LocalDate,
    ): List<InvestmentPortfolioQuotaEntity>

    @Query(
        """
        SELECT q FROM InvestmentPortfolioQuotaEntity q
        WHERE q.financialAccountId = :financialAccountId
          AND q.dimensionType = :dimensionType
          AND q.dimensionKey = :dimensionKey
          AND q.quotaDate BETWEEN :from AND :to
        ORDER BY q.quotaDate ASC
        """,
    )
    fun findByAccountAndDateRange(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioQuotaEntity>

    @Query(
        """
        SELECT q FROM InvestmentPortfolioQuotaEntity q
        WHERE q.financialAccountId = :financialAccountId
          AND q.dimensionType = :dimensionType
          AND q.dimensionKey = :dimensionKey
          AND q.quotaDate = :quotaDate
        """,
    )
    fun findByKey(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String,
        quotaDate: LocalDate,
    ): InvestmentPortfolioQuotaEntity?

    @Query(
        """
        INSERT INTO banking.investment_portfolio_quota
            (financial_account_id, dimension_type, dimension_key, quota_date,
             quota_value, total_quotas, total_portfolio_value, has_estimated_values,
             created_at, updated_at)
        VALUES
            (:financialAccountId, :dimensionType, :dimensionKey, :quotaDate,
             :quotaValue, :totalQuotas, :totalPortfolioValue, :hasEstimatedValues,
             NOW(), NOW())
        ON CONFLICT (financial_account_id, dimension_type, dimension_key, quota_date)
        DO UPDATE SET
            quota_value           = EXCLUDED.quota_value,
            total_quotas          = EXCLUDED.total_quotas,
            total_portfolio_value = EXCLUDED.total_portfolio_value,
            has_estimated_values  = EXCLUDED.has_estimated_values,
            updated_at            = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        financialAccountId: UUID,
        dimensionType: String,
        dimensionKey: String,
        quotaDate: LocalDate,
        quotaValue: BigDecimal,
        totalQuotas: BigDecimal,
        totalPortfolioValue: BigDecimal,
        hasEstimatedValues: Boolean,
    )

    @Query(
        """
        DELETE FROM banking.investment_portfolio_quota
        WHERE financial_account_id = :financialAccountId
          AND quota_date >= :from
        """,
        nativeQuery = true,
    )
    fun deleteFromDate(
        financialAccountId: UUID,
        from: LocalDate,
    ): Int
}
