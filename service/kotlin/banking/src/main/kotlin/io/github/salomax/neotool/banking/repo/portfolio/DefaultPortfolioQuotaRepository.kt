package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioValueWindow
import io.github.salomax.neotool.banking.domain.toBigDecimalOrZero
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

    override fun findEarliestPortfolioDate(financialAccountId: UUID): LocalDate? =
        entityRepository.findEarliestPortfolioDate(financialAccountId)

    override fun findEarliestPortfolioDates(financialAccountIds: Collection<UUID>): Map<UUID, LocalDate> {
        if (financialAccountIds.isEmpty()) return emptyMap()
        val ids = financialAccountIds.toList()
        val placeholders = ids.indices.joinToString(", ") { "CAST(:p$it AS uuid)" }
        val sql = """
            SELECT financial_account_id, MIN(quota_date)
              FROM banking.investment_portfolio_quota
             WHERE financial_account_id IN ($placeholders)
               AND dimension_type = 'PORTFOLIO'
               AND dimension_key = ''
             GROUP BY financial_account_id
        """.trimIndent()
        val query = entityManager.createNativeQuery(sql)
        ids.forEachIndexed { i, id -> query.setParameter("p$i", id.toString()) }
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.associate { row ->
            val financialAccountId = when (val raw = row[0]) {
                is UUID -> raw
                else -> UUID.fromString(raw.toString())
            }
            val earliestDate = when (val raw = row[1]) {
                is LocalDate -> raw
                is java.sql.Date -> raw.toLocalDate()
                else -> LocalDate.parse(raw.toString())
            }
            financialAccountId to earliestDate
        }
    }

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

    override fun findByAccountsAndDateRange(
        financialAccountIds: List<UUID>,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioQuota> {
        if (financialAccountIds.isEmpty()) return emptyList()
        return entityRepository.findByAccountsAndDateRange(
            financialAccountIds = financialAccountIds,
            dimensionType = dimensionType,
            dimensionKey = dimensionKey.orEmpty(),
            from = from,
            to = to,
        ).map(InvestmentPortfolioQuotaMapper::toDomain)
    }

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

    override fun findByTenantAndDateRange(
        accountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioQuota> =
        entityRepository.findByTenantAndDateRange(
            accountId = accountId,
            accountType = FinancialAccountType.INVESTMENT,
            dimensionType = dimensionType,
            dimensionKey = dimensionKey.orEmpty(),
            from = from,
            to = to,
        ).map(InvestmentPortfolioQuotaMapper::toDomain)

    override fun findOpeningAndClosingPortfolioValues(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): PortfolioValueWindow {
        val accountsFilter =
            if (financialAccountId != null) {
                """
                id = CAST(:financialAccountId AS uuid)
                  AND account_id = CAST(:accountId AS uuid)
                """.trimIndent()
            } else {
                "account_id = CAST(:accountId AS uuid)"
            }

        val sql = """
            WITH accounts AS (
                SELECT id
                FROM banking.financial_accounts
                WHERE $accountsFilter
                  AND type = 'INVESTMENT'
                  AND deleted_at IS NULL
            ),
            opening AS (
                SELECT DISTINCT ON (q.financial_account_id)
                       q.financial_account_id,
                       q.total_portfolio_value AS opening_value
                FROM banking.investment_portfolio_quota q
                JOIN accounts a ON a.id = q.financial_account_id
                WHERE q.dimension_type = 'PORTFOLIO'
                  AND q.dimension_key = ''
                  AND q.quota_date < make_date(:startYear, :startMonth, 1)
                ORDER BY q.financial_account_id, q.quota_date DESC
            ),
            closing AS (
                SELECT DISTINCT ON (q.financial_account_id)
                       q.financial_account_id,
                       q.total_portfolio_value AS closing_value
                FROM banking.investment_portfolio_quota q
                JOIN accounts a ON a.id = q.financial_account_id
                WHERE q.dimension_type = 'PORTFOLIO'
                  AND q.dimension_key = ''
                  AND q.quota_date <= make_date(:endYear, :endMonth, 1) + INTERVAL '1 month' - INTERVAL '1 day'
                ORDER BY q.financial_account_id, q.quota_date DESC
            )
            SELECT
                COALESCE(SUM(o.opening_value), 0) AS opening_portfolio_value,
                COALESCE(SUM(c.closing_value), 0) AS closing_portfolio_value
            FROM opening o
            FULL OUTER JOIN closing c USING (financial_account_id)
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("accountId", accountId.toString())
        if (financialAccountId != null) {
            query.setParameter("financialAccountId", financialAccountId.toString())
        }
        query.setParameter("startYear", startYear)
        query.setParameter("startMonth", startMonth)
        query.setParameter("endYear", endYear)
        query.setParameter("endMonth", endMonth)

        @Suppress("UNCHECKED_CAST")
        val row = query.singleResult as Array<Any?>
        return PortfolioValueWindow(
            openingPortfolioValue = row[0].toBigDecimalOrZero(),
            closingPortfolioValue = row[1].toBigDecimalOrZero(),
        )
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
