package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.graphql.investment.BenchmarkPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
import io.github.salomax.neotool.banking.graphql.investment.PortfolioPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.RecalculationStatusDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InvestmentPortfolioSummaryServiceTest {
    private val investmentQueryService: InvestmentQueryService = mock()
    private val portfolioPerformanceQueryService: PortfolioPerformanceQueryService = mock()
    private val service = InvestmentPortfolioSummaryService(investmentQueryService, portfolioPerformanceQueryService)

    @Test
    fun `aggregates holdings and cash into portfolio summary`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()

        whenever(investmentQueryService.getHoldings(financialAccountId, accountId)).thenReturn(
            listOf(
                holding(
                    assetType = AssetType.STOCK,
                    quantity = 10.0,
                    averageCost = 100.0,
                    grossValue = 1200.0,
                    netValue = 1200.0,
                    valuationSource = ValuationSource.MARKET_DATA,
                    valuationReferenceDate = LocalDate.of(2026, 4, 2),
                ),
                holding(
                    assetType = AssetType.CDB,
                    quantity = 5.0,
                    averageCost = 200.0,
                    grossValue = 1100.0,
                    netValue = 1080.0,
                    incomeTax = 20.0,
                    valuationSource = ValuationSource.ESTIMATED,
                    valuationReferenceDate = LocalDate.of(2026, 4, 3),
                ),
            ),
        )
        whenever(investmentQueryService.getInvestmentCashBalance(financialAccountId, accountId)).thenReturn(BigDecimal("250.00"))
        whenever(
            portfolioPerformanceQueryService.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = true,
            ),
        ).thenReturn(
            PortfolioPerformanceDTO(
                twr = 0.0845,
                twrSince = LocalDate.of(2025, 12, 31),
                twrAbsolute = null,
                period = PerformancePeriodDTO.YTD,
                benchmarks = listOf(BenchmarkPerformanceDTO(code = "CDI", label = "CDI", twr = 0.0321)),
                hasEstimatedValues = true,
            ),
        )
        whenever(portfolioPerformanceQueryService.getAssetGroupPerformance(financialAccountId, accountId, "variableIncome", PerformancePeriodDTO.YTD))
            .thenReturn(0.12)
        whenever(portfolioPerformanceQueryService.getAssetGroupPerformance(financialAccountId, accountId, "fixedIncome", PerformancePeriodDTO.YTD))
            .thenReturn(0.04)
        whenever(portfolioPerformanceQueryService.getRecalculationStatus(accountId)).thenReturn(
            RecalculationStatusDTO(
                isCalculating = true,
                jobId = UUID.randomUUID(),
                startedAt = Instant.parse("2026-04-03T10:00:00Z"),
                message = "Historical performance recalculation is in progress.",
            ),
        )

        val summary = service.getSummary(financialAccountId, accountId, PerformancePeriodDTO.YTD)

        assertThat(summary.totalHoldingsCount).isEqualTo(2)
        assertThat(summary.totalCostBasis).isEqualTo(2000.0)
        assertThat(summary.totalGrossValue).isEqualTo(2300.0)
        assertThat(summary.totalNetValue).isEqualTo(2280.0)
        assertThat(summary.cashBalance).isEqualTo(250.0)
        assertThat(summary.totalPortfolioValue).isEqualTo(2550.0)
        assertThat(summary.grossGain).isEqualTo(300.0)
        assertThat(summary.grossGainPercent).isEqualTo(0.15)
        assertThat(summary.totalIncomeTax).isEqualTo(20.0)
        assertThat(summary.hasEstimatedValues).isTrue()
        assertThat(summary.valuationReferenceDate).isEqualTo(LocalDate.of(2026, 4, 3))
        assertThat(summary.currencyCode).isEqualTo("BRL")
        assertThat(summary.byAssetGroup.map { it.groupKey }).containsExactly("variableIncome", "fixedIncome", "cash")
        assertThat(summary.byAssetGroup.first { it.groupKey == "cash" }.allocationPercent).isEqualTo(0.09803922)
        assertThat(summary.byAssetGroup.first { it.groupKey == "variableIncome" }.twr).isEqualTo(0.12)
        assertThat(summary.byAssetGroup.first { it.groupKey == "fixedIncome" }.twr).isEqualTo(0.04)
        assertThat(summary.byAssetGroup.first { it.groupKey == "cash" }.twr).isNull()
        assertThat(summary.performance.period).isEqualTo(PerformancePeriodDTO.YTD)
        assertThat(summary.performance.twr).isEqualTo(0.0845)
        assertThat(summary.performance.benchmarks).singleElement().extracting("code").isEqualTo("CDI")
        assertThat(summary.recalculationStatus.isCalculating).isTrue()
        verify(portfolioPerformanceQueryService).getPortfolioPerformance(financialAccountId, accountId, PerformancePeriodDTO.YTD, true)
        verify(portfolioPerformanceQueryService).getRecalculationStatus(accountId)
    }

    private fun holding(
        assetType: AssetType,
        quantity: Double,
        averageCost: Double,
        grossValue: Double?,
        netValue: Double?,
        incomeTax: Double? = null,
        valuationSource: ValuationSource,
        valuationReferenceDate: LocalDate?,
    ): InvestmentHoldingDTO {
        val now = Instant.parse("2026-04-03T12:00:00Z")
        return InvestmentHoldingDTO(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            assetId = UUID.randomUUID(),
            asset = InvestmentAssetDTO(
                id = UUID.randomUUID(),
                ticker = null,
                isinCode = null,
                name = assetType.name,
                assetType = assetType,
                exchange = null,
                currencyCode = "BRL",
                issuerCnpj = null,
                issuerName = null,
                fundCnpj = null,
                anbimaCategory = null,
                anbimaClass = null,
                anbimaSubclass = null,
                logoAsset = null,
                priceFactor = 1.0,
                createdAt = now,
                updatedAt = now,
            ),
            financialAccount = null,
            quantity = quantity,
            averageCost = averageCost,
            indexer = null,
            indexerPercentage = null,
            fixedAnnualRate = null,
            issueDate = null,
            maturityDate = null,
            gracePeriodDate = null,
            isTaxExempt = false,
            grossValue = grossValue,
            netValue = netValue,
            incomeTax = incomeTax,
            iof = null,
            lastValuedAt = null,
            pricingMode = null,
            valuationSource = valuationSource,
            valuationReferenceDate = valuationReferenceDate,
            isExpired = false,
            isClosed = false,
            isExpiredPending = false,
            closedAt = null,
            closeReason = null,
            source = InvestmentSource.MANUAL,
            createdAt = now,
            updatedAt = now,
        )
    }
}
