package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("ComeCotasSweepJob")
class ComeCotasSweepJobTest {
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()
    private val transactionRepository: InvestmentTransactionRepository = mock()
    private val jobRepository: ComeCotasCheckpointJobRepository = mock()
    private val fundMarketDataProvider: FundMarketDataProvider = mock()

    @Test
    fun `does not enqueue a checkpoint already satisfied by imported withholding tax within reconciliation window`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val checkpointDate = LocalDate.of(2026, 5, 29)
        val today = LocalDate.of(2026, 7, 16)
        val fundCnpj = "12345678000199"

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("88.00000000"),
                averageCost = BigDecimal("1.00000000"),
            )
        val asset =
            Asset(
                id = assetId,
                name = "Fundo RF",
                assetType = AssetType.MUTUAL_FUND,
                fundCnpj = fundCnpj,
            )
        val transactions =
            listOf(
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100.00000000"),
                    unitPrice = BigDecimal("1.00000000"),
                    grossValue = BigDecimal("100.00"),
                    netValue = BigDecimal("100.00"),
                    tradeDate = LocalDate.of(2026, 1, 2),
                    settlementDate = LocalDate.of(2026, 1, 2),
                    source = InvestmentSource.MANUAL,
                    createdAt = Instant.parse("2026-01-02T10:00:00Z"),
                    updatedAt = Instant.parse("2026-01-02T10:00:00Z"),
                ),
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.WITHHOLDING_TAX,
                    quantity = BigDecimal("12.00000000"),
                    unitPrice = BigDecimal("1.50000000"),
                    grossValue = BigDecimal("18.00"),
                    netValue = BigDecimal("18.00"),
                    incomeTax = BigDecimal("18.00"),
                    tradeDate = checkpointDate.minusDays(1),
                    settlementDate = checkpointDate.minusDays(1),
                    source = InvestmentSource.OPEN_FINANCE,
                    providerTransactionId = "ofb-comecotas-2026-05",
                    createdAt = Instant.parse("2026-05-28T10:00:00Z"),
                    updatedAt = Instant.parse("2026-05-28T10:00:00Z"),
                ),
            )

        whenever(holdingRepository.findOpenComeCotasCandidates()).thenReturn(listOf(holding))
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(
            mapOf(
                fundCnpj to
                    FundMarketSnapshot(
                        fundCnpj = fundCnpj,
                        assetType = AssetType.MUTUAL_FUND.name,
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        latestNav = null,
                    ),
            ),
        )
        whenever(transactionRepository.findByHoldingIds(listOf(holdingId), accountId)).thenReturn(transactions)
        whenever(jobRepository.saveIfAbsent(any())).thenReturn(true)

        val job =
            ComeCotasSweepJob(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                transactionRepository = transactionRepository,
                jobRepository = jobRepository,
                fundMarketDataProvider = fundMarketDataProvider,
                batchSize = 100,
                since = LocalDate.of(2024, 1, 1),
                clock = { today },
            )

        job.sweep()

        verify(jobRepository, never()).saveIfAbsent(any<ComeCotasCheckpointJob>())
        verify(transactionRepository).findByHoldingIds(eq(listOf(holdingId)), eq(accountId))
    }

    @Test
    fun `skips non mutual fund candidates before fund regime resolution would throw`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val fundCnpj = "12345678000199"

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("88.00000000"),
                averageCost = BigDecimal("1.00000000"),
            )
        val asset =
            Asset(
                id = assetId,
                name = "Equity",
                assetType = AssetType.STOCK,
                fundCnpj = fundCnpj,
            )
        val transactions =
            listOf(
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100.00000000"),
                    unitPrice = BigDecimal("1.00000000"),
                    grossValue = BigDecimal("100.00"),
                    netValue = BigDecimal("100.00"),
                    tradeDate = LocalDate.of(2026, 1, 2),
                    settlementDate = LocalDate.of(2026, 1, 2),
                    source = InvestmentSource.MANUAL,
                    createdAt = Instant.parse("2026-01-02T10:00:00Z"),
                    updatedAt = Instant.parse("2026-01-02T10:00:00Z"),
                ),
            )

        whenever(holdingRepository.findOpenComeCotasCandidates()).thenReturn(listOf(holding))
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(
            mapOf(
                fundCnpj to
                    FundMarketSnapshot(
                        fundCnpj = fundCnpj,
                        assetType = AssetType.STOCK.name,
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        latestNav = null,
                    ),
            ),
        )
        whenever(transactionRepository.findByHoldingIds(listOf(holdingId), accountId)).thenReturn(transactions)

        val job =
            ComeCotasSweepJob(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                transactionRepository = transactionRepository,
                jobRepository = jobRepository,
                fundMarketDataProvider = fundMarketDataProvider,
                batchSize = 100,
                since = LocalDate.of(2024, 1, 1),
                clock = { LocalDate.of(2026, 7, 16) },
            )

        job.sweep()

        verify(jobRepository, never()).saveIfAbsent(any<ComeCotasCheckpointJob>())
    }
}
