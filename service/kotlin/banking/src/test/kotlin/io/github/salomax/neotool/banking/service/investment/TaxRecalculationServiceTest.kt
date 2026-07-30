package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.AnbimaCategory
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint
import io.github.salomax.neotool.banking.domain.investment.FundMarketNav
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationJob
import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationStatus
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.investment.TaxRecalculationJobRepository
import jakarta.inject.Provider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TaxRecalculationServiceTest {
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()
    private val checkpointRepository: ComeCotasCheckpointRepository = mock()
    private val investmentTransactionRepository: InvestmentTransactionRepository = mock()
    private val fundMarketDataProvider: FundMarketDataProvider = mock()
    private val confirmWithholdingTaxService: ConfirmWithholdingTaxService = mock()
    private val portfolioFlowEventService: PortfolioFlowEventService = mock()
    private val taxRecalculationJobRepository: TaxRecalculationJobRepository = mock()

    private val service =
        TaxRecalculationService(
            holdingRepository = holdingRepository,
            assetRepository = assetRepository,
            checkpointRepository = checkpointRepository,
            investmentTransactionRepository = investmentTransactionRepository,
            fundMarketDataProvider = fundMarketDataProvider,
            confirmWithholdingTaxServiceProvider =
                object : Provider<ConfirmWithholdingTaxService> {
                    override fun get(): ConfirmWithholdingTaxService = confirmWithholdingTaxService
                },
            portfolioFlowEventService = portfolioFlowEventService,
            taxRecalculationJobRepository = taxRecalculationJobRepository,
        )

    private val holdingId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()

    @Test
    fun `fixed-income holding never enqueues tax recalculation`() {
        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding())
        whenever(assetRepository.findById(assetId)).thenReturn(asset(type = AssetType.CDB))

        val result =
            service.requestRecalculationForTransaction(
                holdingId = holdingId,
                accountId = accountId,
                tradeDate = LocalDate.of(2026, 4, 10),
                triggeredBy = "investment_tx:test",
            )

        assertThat(result).isNull()
        verify(taxRecalculationJobRepository, never()).save(any())
        verify(taxRecalculationJobRepository, never()).update(any())
    }

    @Test
    fun `fund holding without periodic withholding never enqueues tax recalculation`() {
        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding())
        whenever(assetRepository.findById(assetId)).thenReturn(
            asset(type = AssetType.MUTUAL_FUND, anbimaCategory = AnbimaCategory.ACOES),
        )

        val result =
            service.requestRecalculationForTransaction(
                holdingId = holdingId,
                accountId = accountId,
                tradeDate = LocalDate.of(2026, 4, 10),
                triggeredBy = "investment_tx:test",
            )

        assertThat(result).isNull()
        verify(taxRecalculationJobRepository, never()).save(any())
        verify(taxRecalculationJobRepository, never()).update(any())
    }

    @Test
    fun `long profile fund enqueues from earliest checkpoint on or after trade date`() {
        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding())
        whenever(assetRepository.findById(assetId)).thenReturn(
            asset(type = AssetType.MUTUAL_FUND, anbimaCategory = AnbimaCategory.MULTIMERCADO),
        )
        whenever(taxRecalculationJobRepository.findPendingByHolding(holdingId)).thenReturn(null)
        whenever(taxRecalculationJobRepository.findProcessingByHolding(holdingId)).thenReturn(null)
        whenever(taxRecalculationJobRepository.save(any())).thenAnswer { inv ->
            val job = inv.arguments[0] as TaxRecalculationJob
            job.copy(id = UUID.randomUUID())
        }

        val result =
            service.requestRecalculationForTransaction(
                holdingId = holdingId,
                accountId = accountId,
                tradeDate = LocalDate.of(2026, 4, 10),
                triggeredBy = "investment_tx:test",
            )

        assertThat(result).isNotNull
        assertThat(result!!.recalculateFrom).isEqualTo(LocalDate.of(2026, 5, 29))
        assertThat(result.status).isEqualTo(TaxRecalculationStatus.PENDING)
        verify(taxRecalculationJobRepository).save(any())
    }

    @Test
    fun `second pending request merges to earliest recalculation date`() {
        val existing =
            TaxRecalculationJob(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                status = TaxRecalculationStatus.PENDING,
                recalculateFrom = LocalDate.of(2026, 11, 30),
                triggeredBy = "old",
                createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                updatedAt = Instant.parse("2026-06-01T10:00:00Z"),
            )
        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding())
        whenever(taxRecalculationJobRepository.findPendingByHolding(holdingId)).thenReturn(existing)
        whenever(taxRecalculationJobRepository.update(any())).thenAnswer { it.arguments.first() }

        val updated =
            service.requestRecalculation(
                holdingId = holdingId,
                accountId = accountId,
                recalculateFrom = LocalDate.of(2026, 5, 29),
                triggeredBy = "new-trigger",
            )

        assertThat(updated.recalculateFrom).isEqualTo(LocalDate.of(2026, 5, 29))
        assertThat(updated.triggeredBy).isEqualTo("new-trigger")
        verify(taxRecalculationJobRepository, never()).save(any())
    }

    @Test
    fun `no-op recompute completes without reconciling withholding or requesting portfolio recalculation`() {
        val checkpointDate = LocalDate.of(2026, 5, 29)
        val claimed =
            TaxRecalculationJob(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                status = TaxRecalculationStatus.PROCESSING,
                recalculateFrom = checkpointDate,
                triggeredBy = "investment_tx:test",
                createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                updatedAt = Instant.parse("2026-06-01T10:00:00Z"),
            )
        whenever(taxRecalculationJobRepository.tryClaimJob(eq(requireNotNull(claimed.id)), any())).thenReturn(claimed)
        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding(comeCotasEnabled = true))
        whenever(assetRepository.findById(assetId)).thenReturn(
            asset(type = AssetType.MUTUAL_FUND, anbimaCategory = AnbimaCategory.MULTIMERCADO),
        )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(any())).thenReturn(
            mapOf(
                "12345678000190" to
                    FundMarketSnapshot(
                        fundCnpj = "12345678000190",
                        assetType = AssetType.MUTUAL_FUND.name,
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        latestNav = FundMarketNav(checkpointDate, BigDecimal("1.10")),
                    ),
            ),
        )
        whenever(
            fundMarketDataProvider.getNavSeries("12345678000190", LocalDate.of(2026, 1, 2), checkpointDate),
        ).thenReturn(
            listOf(checkpointDate to BigDecimal("1.10")),
        )
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(
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
                ),
            ),
        )
        whenever(checkpointRepository.findByHoldingAndDateRange(holdingId, checkpointDate, checkpointDate)).thenReturn(
            listOf(
                ComeCotasCheckpoint(
                    holdingId = holdingId,
                    checkpointDate = checkpointDate,
                    computedAmount = BigDecimal("1.50"),
                    withheldAmount = BigDecimal("1.50"),
                ),
            ),
        )
        whenever(
            checkpointRepository.findByHoldingAndDateRange(holdingId, LocalDate.of(2026, 1, 2), checkpointDate),
        ).thenReturn(
            emptyList(),
        )
        whenever(taxRecalculationJobRepository.update(any())).thenAnswer { it.arguments.first() }

        service.process(requireNotNull(claimed.id))

        verifyNoInteractions(confirmWithholdingTaxService)
        verify(portfolioFlowEventService, never()).requestRecalculation(any(), any(), any(), any())
        verify(taxRecalculationJobRepository).update(any())
    }

    private fun holding(comeCotasEnabled: Boolean? = null) =
        Holding(
            id = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            quantity = BigDecimal.ONE,
            averageCost = BigDecimal.ONE,
            comeCotasEnabled = comeCotasEnabled,
        )

    private fun asset(
        type: AssetType,
        anbimaCategory: AnbimaCategory? = null,
    ) = Asset(
        id = assetId,
        name = "Asset",
        assetType = type,
        anbimaCategory = anbimaCategory,
        fundCnpj = "12.345.678/0001-90",
    )
}
