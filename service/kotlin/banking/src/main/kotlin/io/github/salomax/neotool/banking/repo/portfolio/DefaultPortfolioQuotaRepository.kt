package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioQuotaMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.LocalDate
import java.util.UUID


@Singleton
open class DefaultPortfolioQuotaRepository(
    private val entityRepository: PortfolioQuotaEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : PortfolioQuotaRepository {
    @Transactional
    override fun upsert(quota: InvestmentPortfolioQuota): InvestmentPortfolioQuota {
        val normalizedDimensionKey = quota.dimensionKey.orEmpty()
        entityRepository.upsert(
            financialAccountId = quota.financialAccountId,
            dimensionType = quota.dimensionType.name,
            dimensionKey = normalizedDimensionKey,
            quotaDate = quota.quotaDate,
            quotaValue = quota.quotaValue,
            totalQuotas = quota.totalQuotas,
            totalPortfolioValue = quota.totalPortfolioValue,
            hasEstimatedValues = quota.hasEstimatedValues,
        )
        entityManager.clear()
        return entityRepository.findByKey(
            financialAccountId = quota.financialAccountId,
            dimensionType = quota.dimensionType,
            dimensionKey = normalizedDimensionKey,
            quotaDate = quota.quotaDate,
        )?.let(InvestmentPortfolioQuotaMapper::toDomain)
            ?: throw IllegalStateException(
                "Portfolio quota upsert failed for ${quota.financialAccountId} @ ${quota.quotaDate}",
            )
    }

    override fun findLatestBefore(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        date: LocalDate,
    ): InvestmentPortfolioQuota? =
        entityRepository.findLatestBefore(
            financialAccountId = financialAccountId,
            dimensionType = dimensionType,
            dimensionKey = dimensionKey.orEmpty(),
            date = date,
        ).firstOrNull()
            ?.let(InvestmentPortfolioQuotaMapper::toDomain)

    override fun findByAccountAndDateRange(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioQuota> =
        entityRepository.findByAccountAndDateRange(
            financialAccountId = financialAccountId,
            dimensionType = dimensionType,
            dimensionKey = dimensionKey.orEmpty(),
            from = from,
            to = to,
        ).map(InvestmentPortfolioQuotaMapper::toDomain)

    /**
     * Upserts all [quotas] in a single DB round-trip using a multi-row VALUES clause.
     * Returns the input list — callers use it directly for next-iteration math without re-reading from DB.
     */
    @Transactional
    override fun batchUpsert(quotas: List<InvestmentPortfolioQuota>): List<InvestmentPortfolioQuota> {
        if (quotas.isEmpty()) return emptyList()
        val query = entityManager.createNativeQuery(buildBatchUpsertSql(quotas.size))
        quotas.forEachIndexed { i, quota ->
            query.setParameter("financialAccountId$i", quota.financialAccountId)
            query.setParameter("dimensionType$i", quota.dimensionType.name)
            query.setParameter("dimensionKey$i", quota.dimensionKey.orEmpty())
            query.setParameter("quotaDate$i", quota.quotaDate)
            query.setParameter("quotaValue$i", quota.quotaValue)
            query.setParameter("totalQuotas$i", quota.totalQuotas)
            query.setParameter("totalPortfolioValue$i", quota.totalPortfolioValue)
            query.setParameter("hasEstimatedValues$i", quota.hasEstimatedValues)
        }
        query.executeUpdate()
        entityManager.clear()
        return quotas
    }

    private fun buildBatchUpsertSql(size: Int): String {
        val values = (0 until size).joinToString(",\n        ") { i ->
            "(:financialAccountId$i, :dimensionType$i, :dimensionKey$i, :quotaDate$i, " +
                ":quotaValue$i, :totalQuotas$i, :totalPortfolioValue$i, :hasEstimatedValues$i, NOW(), NOW())"
        }
        return """
            INSERT INTO banking.investment_portfolio_quota
                (financial_account_id, dimension_type, dimension_key, quota_date,
                 quota_value, total_quotas, total_portfolio_value, has_estimated_values,
                 created_at, updated_at)
            VALUES
                $values
            ON CONFLICT (financial_account_id, dimension_type, dimension_key, quota_date)
            DO UPDATE SET
                quota_value           = EXCLUDED.quota_value,
                total_quotas          = EXCLUDED.total_quotas,
                total_portfolio_value = EXCLUDED.total_portfolio_value,
                has_estimated_values  = EXCLUDED.has_estimated_values,
                updated_at            = NOW()
        """.trimIndent()
    }

    override fun findLatestBeforeAllDimensions(
        financialAccountId: UUID,
        date: LocalDate,
    ): Map<PortfolioDimensionType, Map<String, InvestmentPortfolioQuota>> =
        entityRepository.findLatestBeforeAllDimensions(financialAccountId, date)
            .map(InvestmentPortfolioQuotaMapper::toDomain)
            .groupBy { it.dimensionType }
            .mapValues { (_, quotas) -> quotas.associateBy { it.dimensionKey.orEmpty() } }

    @Transactional
    override fun deleteFromDate(
        financialAccountId: UUID,
        from: LocalDate,
    ): Int {
        val deleted = entityRepository.deleteFromDate(financialAccountId, from)
        entityManager.clear()
        return deleted
    }
}
