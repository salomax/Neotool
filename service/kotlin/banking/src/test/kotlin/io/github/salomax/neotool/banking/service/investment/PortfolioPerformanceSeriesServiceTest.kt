package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceCapabilitiesDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformancePointDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceSummaryDTO
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PortfolioPerformanceSeriesServiceTest {
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()
    private val investmentQueryService: InvestmentQueryService = mock()

    private val service =
        PortfolioPerformanceSeriesService(
            holdingRepository = holdingRepository,
            assetRepository = assetRepository,
            investmentQueryService = investmentQueryService,
            clock = { LocalDate.of(2026, 4, 8) },
        )

    @Test
    fun `empty portfolio returns empty points and zero counts`() {
        val accountId = UUID.randomUUID()
        whenever(holdingRepository.findByAccount(accountId)).thenReturn(emptyList())

        val result = service.getPortfolioPerformance(accountId, null, null, null, HoldingPerformanceResolution.MONTH, includeProjection = false)

        assertThat(result.points).isEmpty()
        assertThat(result.includedHoldingsCount).isEqualTo(0)
        assertThat(result.excludedHoldingsCount).isEqualTo(0)
        assertThat(result.summary.grossValue).isEqualTo(0.0)
    }

    @Test
    fun `aggregates mixed fixed income and fund holdings by month`() {
        val accountId = UUID.randomUUID()
        val fixedHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val fundHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val fixedAsset = asset(fixedHolding.assetId, AssetType.CDB, "BRL")
        val fundAsset = asset(fundHolding.assetId, AssetType.MUTUAL_FUND, "BRL")
        whenever(holdingRepository.findByAccount(accountId)).thenReturn(listOf(fixedHolding, fundHolding))
        whenever(assetRepository.findByIds(setOf(fixedAsset.id!!, fundAsset.id!!))).thenReturn(listOf(fixedAsset, fundAsset))
        whenever(investmentQueryService.computeHoldingPerformance(eq(fixedHolding), eq(fixedAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(
                perf(
                    fixedHolding.id!!,
                    fixedAsset.assetType,
                    "BRL",
                    listOf(point("2026-03-31T00:00:00Z", 100.0, 105.0), point("2026-04-30T00:00:00Z", 100.0, 110.0)),
                ),
            )
        whenever(investmentQueryService.computeHoldingPerformance(eq(fundHolding), eq(fundAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(
                perf(
                    fundHolding.id!!,
                    fundAsset.assetType,
                    "BRL",
                    listOf(point("2026-04-30T00:00:00Z", 50.0, 53.0)),
                ),
            )

        val result = service.getPortfolioPerformance(accountId, null, null, null, HoldingPerformanceResolution.MONTH, includeProjection = false)

        assertThat(result.points).hasSize(2)
        assertThat(result.points.last().grossValue).isEqualTo(163.0)
        assertThat(result.includedHoldingsCount).isEqualTo(2)
        assertThat(result.excludedHoldingsCount).isEqualTo(0)
    }

    @Test
    fun `unsupported holding is counted as excluded`() {
        val accountId = UUID.randomUUID()
        val unsupportedHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val unsupportedAsset = asset(unsupportedHolding.assetId, AssetType.OTHER, "BRL")
        whenever(holdingRepository.findByAccount(accountId)).thenReturn(listOf(unsupportedHolding))
        whenever(assetRepository.findByIds(setOf(unsupportedAsset.id!!))).thenReturn(listOf(unsupportedAsset))
        whenever(investmentQueryService.computeHoldingPerformance(eq(unsupportedHolding), eq(unsupportedAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(null)

        val result = service.getPortfolioPerformance(accountId, null, null, null, HoldingPerformanceResolution.MONTH, includeProjection = false)

        assertThat(result.includedHoldingsCount).isEqualTo(0)
        assertThat(result.excludedHoldingsCount).isEqualTo(1)
        assertThat(result.points).isEmpty()
    }

    @Test
    fun `financialAccountId scopes open holdings query to custodian account`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val fixedHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val fixedAsset = asset(fixedHolding.assetId, AssetType.CDB, "BRL")
        whenever(holdingRepository.findOpenByFinancialAccount(financialAccountId, accountId)).thenReturn(listOf(fixedHolding))
        whenever(assetRepository.findByIds(setOf(fixedAsset.id!!))).thenReturn(listOf(fixedAsset))
        whenever(investmentQueryService.computeHoldingPerformance(eq(fixedHolding), eq(fixedAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(perf(fixedHolding.id!!, fixedAsset.assetType, "BRL", listOf(point("2026-04-30T00:00:00Z", 100.0, 110.0))))

        val result =
            service.getPortfolioPerformance(
                accountId = accountId,
                groupKey = AssetGroupMapping.BANK_FIXED_INCOME,
                subgroupKey = null,
                assetTypes = null,
                resolution = HoldingPerformanceResolution.MONTH,
                includeProjection = false,
                financialAccountId = financialAccountId,
            )

        assertThat(result.includedHoldingsCount).isEqualTo(1)
        verify(holdingRepository).findOpenByFinancialAccount(financialAccountId, accountId)
        verify(holdingRepository, never()).findOpenByAccount(accountId)
    }

    @Test
    fun `financialAccountId scopes full holdings when groupKey is null`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        whenever(holdingRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(emptyList())

        service.getPortfolioPerformance(
            accountId,
            null,
            null,
            null,
            HoldingPerformanceResolution.MONTH,
            includeProjection = false,
            financialAccountId = financialAccountId,
        )

        verify(holdingRepository).findByFinancialAccount(financialAccountId, accountId)
        verify(holdingRepository, never()).findByAccount(accountId)
    }

    @Test
    fun `groupKey filters holdings by mapped asset group`() {
        val accountId = UUID.randomUUID()
        val fixedHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val fundHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val fixedAsset = asset(fixedHolding.assetId, AssetType.CDB, "BRL")
        val fundAsset = asset(fundHolding.assetId, AssetType.MUTUAL_FUND, "BRL")
        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(fixedHolding, fundHolding))
        whenever(assetRepository.findByIds(setOf(fixedAsset.id!!, fundAsset.id!!))).thenReturn(listOf(fixedAsset, fundAsset))
        whenever(investmentQueryService.computeHoldingPerformance(eq(fixedHolding), eq(fixedAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(perf(fixedHolding.id!!, fixedAsset.assetType, "BRL", listOf(point("2026-04-30T00:00:00Z", 100.0, 110.0))))

        val result = service.getPortfolioPerformance(
            accountId,
            AssetGroupMapping.BANK_FIXED_INCOME,
            null,
            null,
            HoldingPerformanceResolution.MONTH,
            includeProjection = false,
        )

        assertThat(result.includedHoldingsCount).isEqualTo(1)
        assertThat(result.points).hasSize(1)
    }

    @Test
    fun `subgroupKey filters by canonical stocks slice`() {
        val accountId = UUID.randomUUID()
        val stockHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val reitHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val stockAsset = asset(stockHolding.assetId, AssetType.STOCK, "BRL")
        val reitAsset = asset(reitHolding.assetId, AssetType.REAL_ESTATE_FUND, "BRL")
        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(stockHolding, reitHolding))
        whenever(assetRepository.findByIds(setOf(stockAsset.id!!, reitAsset.id!!))).thenReturn(listOf(stockAsset, reitAsset))
        whenever(investmentQueryService.computeHoldingPerformance(eq(stockHolding), eq(stockAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(perf(stockHolding.id!!, stockAsset.assetType, "BRL", listOf(point("2026-04-30T00:00:00Z", 100.0, 120.0))))

        val result = service.getPortfolioPerformance(
            accountId = accountId,
            groupKey = null,
            subgroupKey = PortfolioPerformanceSubgroupMapping.PERF_STOCKS,
            assetTypes = null,
            resolution = HoldingPerformanceResolution.MONTH,
            includeProjection = false,
        )

        assertThat(result.includedHoldingsCount).isEqualTo(1)
        assertThat(result.points).hasSize(1)
    }

    @Test
    fun `trims leading inactive months before first group exposure`() {
        val accountId = UUID.randomUUID()
        val stockHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val stockAsset = asset(stockHolding.assetId, AssetType.STOCK, "BRL")
        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(stockHolding))
        whenever(assetRepository.findByIds(setOf(stockAsset.id!!))).thenReturn(listOf(stockAsset))
        whenever(investmentQueryService.computeHoldingPerformance(eq(stockHolding), eq(stockAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(
                perf(
                    stockHolding.id!!,
                    stockAsset.assetType,
                    "BRL",
                    listOf(
                        point("2025-01-31T00:00:00Z", 0.0, 0.0),
                        point("2025-02-28T00:00:00Z", 0.0, 0.0),
                        point("2025-03-31T00:00:00Z", 1000.0, 1100.0),
                    ),
                ),
            )

        val result = service.getPortfolioPerformance(
            accountId = accountId,
            groupKey = null,
            subgroupKey = PortfolioPerformanceSubgroupMapping.PERF_STOCKS,
            assetTypes = null,
            resolution = HoldingPerformanceResolution.MONTH,
            includeProjection = false,
        )

        assertThat(result.points).hasSize(1)
        assertThat(result.points.first().timestamp).isEqualTo(Instant.parse("2025-03-31T23:59:59Z"))
        assertThat(result.points.first().grossValue).isEqualTo(1100.0)
    }

    @Test
    fun `filtered class series ignores closed holdings with stale history`() {
        val accountId = UUID.randomUUID()
        val closedHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId, quantity = BigDecimal.ZERO)
        val openHolding = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val closedAsset = asset(closedHolding.assetId, AssetType.STOCK, "BRL")
        val openAsset = asset(openHolding.assetId, AssetType.STOCK, "BRL")
        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(openHolding))
        whenever(assetRepository.findByIds(setOf(openAsset.id!!))).thenReturn(listOf(openAsset))
        whenever(investmentQueryService.computeHoldingPerformance(eq(openHolding), eq(openAsset), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(
                perf(
                    openHolding.id!!,
                    openAsset.assetType,
                    "BRL",
                    listOf(
                        point("2026-01-31T00:00:00Z", 100.0, 105.0),
                        point("2026-02-28T00:00:00Z", 100.0, 110.0),
                    ),
                ),
            )

        val result = service.getPortfolioPerformance(
            accountId = accountId,
            groupKey = null,
            subgroupKey = PortfolioPerformanceSubgroupMapping.PERF_STOCKS,
            assetTypes = null,
            resolution = HoldingPerformanceResolution.MONTH,
            includeProjection = false,
        )

        assertThat(result.points).hasSize(2)
        assertThat(result.points.first().timestamp).isEqualTo(Instant.parse("2026-01-31T23:59:59Z"))
        assertThat(result.points.last().grossValue).isEqualTo(110.0)
        verify(holdingRepository).findOpenByAccount(accountId)
        verify(holdingRepository, never()).findByAccount(accountId)
        verify(investmentQueryService, never()).computeHoldingPerformance(eq(closedHolding), eq(closedAsset), eq(accountId), any(), isNull(), isNull(), eq(false))
    }

    @Test
    fun `mixed currencies throw exception`() {
        val accountId = UUID.randomUUID()
        val holdingA = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val holdingB = holding(UUID.randomUUID(), UUID.randomUUID(), accountId)
        val assetA = asset(holdingA.assetId, AssetType.CDB, "BRL")
        val assetB = asset(holdingB.assetId, AssetType.CDB, "USD")
        whenever(holdingRepository.findByAccount(accountId)).thenReturn(listOf(holdingA, holdingB))
        whenever(assetRepository.findByIds(setOf(assetA.id!!, assetB.id!!))).thenReturn(listOf(assetA, assetB))
        whenever(investmentQueryService.computeHoldingPerformance(eq(holdingA), eq(assetA), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(perf(holdingA.id!!, assetA.assetType, "BRL", listOf(point("2026-04-30T00:00:00Z", 100.0, 100.0))))
        whenever(investmentQueryService.computeHoldingPerformance(eq(holdingB), eq(assetB), eq(accountId), any(), isNull(), isNull(), eq(false)))
            .thenReturn(perf(holdingB.id!!, assetB.assetType, "USD", listOf(point("2026-04-30T00:00:00Z", 100.0, 100.0))))

        assertThatThrownBy {
            service.getPortfolioPerformance(accountId, null, null, null, HoldingPerformanceResolution.MONTH, includeProjection = false)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot aggregate performance across multiple currencies")
    }

    private fun holding(id: UUID, assetId: UUID, accountId: UUID, quantity: BigDecimal = BigDecimal.ONE): Holding =
        Holding(
            id = id,
            financialAccountId = UUID.randomUUID(),
            accountId = accountId,
            assetId = assetId,
            quantity = quantity,
            averageCost = BigDecimal.ONE,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    private fun asset(id: UUID, type: AssetType, currencyCode: String): Asset =
        Asset(
            id = id,
            name = "Asset",
            assetType = type,
            currencyCode = currencyCode,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    private fun perf(
        holdingId: UUID,
        assetType: AssetType,
        currencyCode: String,
        points: List<HoldingPerformancePointDTO>,
    ) = HoldingPerformanceDTO(
        holdingId = holdingId,
        assetType = assetType,
        currencyCode = currencyCode,
        resolution = HoldingPerformanceResolution.MONTH,
        valuationSource = ValuationSource.CALCULATED,
        valuationReferenceDate = null,
        capabilities = HoldingPerformanceCapabilitiesDTO(
            supportsProjection = false,
            projectionDefaultEnabled = false,
            supportedResolutions = listOf(HoldingPerformanceResolution.MONTH),
            defaultResolution = HoldingPerformanceResolution.MONTH,
        ),
        summary = HoldingPerformanceSummaryDTO(
            investedPrincipal = points.lastOrNull()?.investedPrincipal,
            netContributions = points.lastOrNull()?.netContributions,
            currentCostBasis = points.lastOrNull()?.currentCostBasis,
            grossValue = points.lastOrNull()?.grossValue,
            netValue = points.lastOrNull()?.netValue,
            incomeTax = points.lastOrNull()?.incomeTax,
            iof = points.lastOrNull()?.iof,
            grossGain = points.lastOrNull()?.grossGain,
            netGain = points.lastOrNull()?.netGain,
            grossGainPercent = null,
        ),
        points = points,
    )

    private fun point(timestamp: String, investedPrincipal: Double, grossValue: Double) =
        HoldingPerformancePointDTO(
            timestamp = Instant.parse(timestamp),
            isProjected = false,
            investedPrincipal = investedPrincipal,
            netContributions = investedPrincipal,
            currentCostBasis = investedPrincipal,
            grossValue = grossValue,
            netValue = grossValue,
            incomeTax = 0.0,
            iof = 0.0,
            grossGain = grossValue - investedPrincipal,
            netGain = grossValue - investedPrincipal,
            quantity = null,
        )
}
