package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.domain.investment.FundMarketNav
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("ComeCotasCheckpointService")
class ComeCotasCheckpointServiceTest {
    private lateinit var jobRepository: ComeCotasCheckpointJobRepository
    private lateinit var holdingRepository: HoldingRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var transactionRepository: InvestmentTransactionRepository
    private lateinit var fundMarketDataProvider: FundMarketDataProvider
    private lateinit var confirmWithholdingTaxService: ConfirmWithholdingTaxService
    private lateinit var service: ComeCotasCheckpointService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val holdingId = UUID.randomUUID()
    private val checkpointDate = LocalDate.of(2026, 11, 30)
    private val fundCnpj = "12345678000199"

    private val holding =
        Holding(
            id = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            quantity = BigDecimal("100.00000000"),
            averageCost = BigDecimal("1.00000000"),
        )

    private val asset =
        Asset(
            id = assetId,
            name = "Fundo CDI",
            assetType = AssetType.MUTUAL_FUND,
            fundCnpj = fundCnpj,
        )

    @BeforeEach
    fun setUp() {
        jobRepository = mock()
        holdingRepository = mock()
        assetRepository = mock()
        transactionRepository = mock()
        fundMarketDataProvider = mock()
        confirmWithholdingTaxService = mock()
        service =
            ComeCotasCheckpointService(
                jobRepository = jobRepository,
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                investmentTransactionRepository = transactionRepository,
                fundMarketDataProvider = fundMarketDataProvider,
                confirmWithholdingTaxService = confirmWithholdingTaxService,
                maxAttempts = 3,
                retryBaseDelay = Duration.ofHours(6),
            )
    }

    @Test
    fun `requeues when checkpoint nav is unavailable`() {
        val job = claimedJob(attemptCount = 1)
        stubBase(
            job,
            holding = holding,
            asset = asset,
            transactions = listOf(purchase(quantity = "100", unitPrice = "1.00")),
        )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(
            mapOf(
                fundCnpj to
                    FundMarketSnapshot(
                        fundCnpj = fundCnpj,
                        assetType = "MUTUAL_FUND",
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        latestNav = FundMarketNav(date = checkpointDate, closePrice = BigDecimal("1.20")),
                    ),
            ),
        )
        whenever(
            fundMarketDataProvider.getNavSeries(fundCnpj, LocalDate.of(2026, 1, 2), checkpointDate),
        ).thenReturn(emptyList())

        service.process(requireNotNull(job.id))

        val updatedJob = captureUpdatedJob()
        assertThat(updatedJob.status).isEqualTo(ComeCotasCheckpointJobStatus.PENDING)
        assertThat(updatedJob.nextAttemptAt).isNotNull
        assertThat(updatedJob.errorCode).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.code)
        verify(confirmWithholdingTaxService, never()).confirm(any(), any(), any())
    }

    @Test
    fun `uses prior withholding tax to materialize only the incremental checkpoint amount`() {
        val job = claimedJob(attemptCount = 1)
        stubBase(
            job,
            holding = holding.copy(quantity = BigDecimal("98.63636364")),
            asset = asset,
            transactions =
                listOf(
                    purchase(quantity = "100", unitPrice = "1.00"),
                    withholdingTax(
                        tradeDate = LocalDate.of(2026, 5, 29),
                        amount = "1.50",
                    ),
                ),
        )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(
            mapOf(
                fundCnpj to
                    FundMarketSnapshot(
                        fundCnpj = fundCnpj,
                        assetType = "MUTUAL_FUND",
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        latestNav = FundMarketNav(date = checkpointDate, closePrice = BigDecimal("1.20")),
                    ),
            ),
        )
        whenever(fundMarketDataProvider.getNavSeries(fundCnpj, LocalDate.of(2026, 1, 2), checkpointDate)).thenReturn(
            listOf(checkpointDate to BigDecimal("1.20")),
        )

        service.process(requireNotNull(job.id))

        val commandCaptor = argumentCaptor<ConfirmWithholdingTaxCommand>()
        verify(confirmWithholdingTaxService).confirm(commandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(commandCaptor.firstValue.source).isEqualTo(InvestmentSource.SYSTEM)
        assertThat(commandCaptor.firstValue.irValue).isEqualByComparingTo("1.50")
        assertThat(commandCaptor.firstValue.quotasDeducted).isEqualByComparingTo("1.25000000")

        val updatedJob = captureUpdatedJob()
        assertThat(updatedJob.status).isEqualTo(ComeCotasCheckpointJobStatus.COMPLETED)
    }

    @Test
    fun `fails when computed quotas exceed current holding quantity`() {
        val job = claimedJob(attemptCount = 1)
        stubBase(
            job,
            holding = holding.copy(quantity = BigDecimal("0.10000000")),
            asset = asset.copy(fundCnpj = fundCnpj),
            transactions = listOf(purchase(quantity = "100", unitPrice = "0.10")),
        )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(
            mapOf(
                fundCnpj to
                    FundMarketSnapshot(
                        fundCnpj = fundCnpj,
                        assetType = "MUTUAL_FUND",
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        latestNav = FundMarketNav(date = checkpointDate, closePrice = BigDecimal("10.00")),
                    ),
            ),
        )
        whenever(fundMarketDataProvider.getNavSeries(fundCnpj, LocalDate.of(2026, 1, 2), checkpointDate)).thenReturn(
            listOf(checkpointDate to BigDecimal("10.00")),
        )

        service.process(requireNotNull(job.id))

        val updatedJob = captureUpdatedJob()
        assertThat(updatedJob.status).isEqualTo(ComeCotasCheckpointJobStatus.FAILED)
        assertThat(updatedJob.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
        verify(confirmWithholdingTaxService, never()).confirm(any(), any(), any())
    }

    @Test
    fun `completes without posting when withholding tax already exists in checkpoint window`() {
        val job = claimedJob(attemptCount = 1)
        stubBase(
            job,
            holding = holding,
            asset = asset,
            transactions = listOf(purchase(quantity = "100", unitPrice = "1.00")),
        )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(
            mapOf(
                fundCnpj to
                    FundMarketSnapshot(
                        fundCnpj = fundCnpj,
                        assetType = "MUTUAL_FUND",
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        latestNav = FundMarketNav(date = checkpointDate, closePrice = BigDecimal("1.20")),
                    ),
            ),
        )
        whenever(
            transactionRepository.existsWithholdingTaxForHoldingInDateRange(
                holdingId,
                accountId,
                checkpointDate.minusDays(ComeCotasSupport.RECONCILIATION_WINDOW_DAYS),
                checkpointDate.plusDays(ComeCotasSupport.RECONCILIATION_WINDOW_DAYS),
            ),
        ).thenReturn(true)

        service.process(requireNotNull(job.id))

        val updatedJob = captureUpdatedJob()
        assertThat(updatedJob.status).isEqualTo(ComeCotasCheckpointJobStatus.COMPLETED)
        verify(confirmWithholdingTaxService, never()).confirm(any(), any(), any())
    }

    private fun stubBase(
        job: ComeCotasCheckpointJob,
        holding: Holding,
        asset: Asset,
        transactions: List<InvestmentTransaction>,
    ) {
        whenever(jobRepository.tryClaimJob(eq(requireNotNull(job.id)), any())).thenReturn(job)
        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(
            transactionRepository.existsWithholdingTaxForHoldingOnTradeDate(holdingId, accountId, checkpointDate),
        ).thenReturn(false)
        whenever(
            transactionRepository.existsWithholdingTaxForHoldingInDateRange(
                holdingId,
                accountId,
                checkpointDate.minusDays(ComeCotasSupport.RECONCILIATION_WINDOW_DAYS),
                checkpointDate.plusDays(ComeCotasSupport.RECONCILIATION_WINDOW_DAYS),
            ),
        ).thenReturn(false)
        whenever(transactionRepository.findByHolding(holdingId, accountId)).thenReturn(transactions)
        whenever(jobRepository.update(any())).thenAnswer { it.arguments[0] as ComeCotasCheckpointJob }
    }

    private fun captureUpdatedJob(): ComeCotasCheckpointJob {
        val captor = argumentCaptor<ComeCotasCheckpointJob>()
        verify(jobRepository).update(captor.capture())
        return captor.firstValue
    }

    private fun claimedJob(attemptCount: Int): ComeCotasCheckpointJob =
        ComeCotasCheckpointJob(
            id = UUID.randomUUID(),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            checkpointDate = checkpointDate,
            status = ComeCotasCheckpointJobStatus.PROCESSING,
            processingNodeId = "node-a",
            attemptCount = attemptCount,
        )

    private fun purchase(
        quantity: String,
        unitPrice: String,
    ): InvestmentTransaction =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = BigDecimal(quantity),
            unitPrice = BigDecimal(unitPrice),
            grossValue = BigDecimal(quantity).multiply(BigDecimal(unitPrice)),
            tradeDate = LocalDate.of(2026, 1, 2),
            createdAt = Instant.parse("2026-01-02T12:00:00Z"),
            updatedAt = Instant.parse("2026-01-02T12:00:00Z"),
        )

    private fun withholdingTax(
        tradeDate: LocalDate,
        amount: String,
    ): InvestmentTransaction =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = InvestmentTransactionType.WITHHOLDING_TAX,
            grossValue = BigDecimal(amount),
            incomeTax = BigDecimal(amount),
            tradeDate = tradeDate,
            createdAt = tradeDate.atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC),
            updatedAt = tradeDate.atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC),
        )
}
