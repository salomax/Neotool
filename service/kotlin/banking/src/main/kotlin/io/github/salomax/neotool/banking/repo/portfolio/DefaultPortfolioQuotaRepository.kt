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
