package io.github.salomax.neotool.banking.service.investment.lifecycle

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
import io.github.salomax.neotool.banking.model.dto.investment.ComeCotasInlineBackfillRequestedEvent
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.micronaut.context.event.ApplicationEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ComeCotasPurchaseBackfillHandlerTest {
    private val jobRepository: ComeCotasCheckpointJobRepository = mock()
    private val transactionRepository: InvestmentTransactionRepository = mock()
    private val fundMarketDataProvider: FundMarketDataProvider = mock()
    private val inlineBackfillRequestedEventPublisher:
        ApplicationEventPublisher<ComeCotasInlineBackfillRequestedEvent> =
        mock()
    private val today = LocalDate.of(2026, 7, 15)
    private val handler =
        ComeCotasPurchaseBackfillHandler(
            jobRepository = jobRepository,
            investmentTransactionRepository = transactionRepository,
            fundMarketDataProvider = fundMarketDataProvider,
            inlineBackfillRequestedEventPublisher = inlineBackfillRequestedEventPublisher,
            inlineMaxCheckpoints = 2,
            inlineMaxAgeDays = 370,
        )

    @Test
    fun `processes recent retroactive checkpoints inline oldest first`() {
        val context = fundPurchaseContext(tradeDate = LocalDate.of(2025, 10, 1))
        val novemberId = UUID.randomUUID()
        val mayId = UUID.randomUUID()
        whenever(transactionRepository.findByHolding(context.holding.id!!, context.accountId))
            .thenReturn(listOf(requireNotNull(context.transaction)))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf("27717359000130")))
            .thenReturn(
                mapOf(
                    "27717359000130" to
                        FundMarketSnapshot(
                            fundCnpj = "27717359000130",
                            assetType = "FIXED_INCOME_FUND",
                            taxProfile = FundTaxProfile.COME_COTAS_LONG,
                            latestNav = null,
                        ),
                ),
            )
        whenever(jobRepository.findByHoldingAndCheckpointDate(any(), any())).thenReturn(null)
        whenever(jobRepository.save(any())).thenAnswer {
            val job = it.arguments[0] as ComeCotasCheckpointJob
            val jobId =
                when (job.checkpointDate.monthValue) {
                    11 -> novemberId
                    5 -> mayId
                    else -> UUID.randomUUID()
                }
            job.copy(id = jobId)
        }

        handler.onRecord(context)

        val captor = argumentCaptor<ComeCotasInlineBackfillRequestedEvent>()
        verify(inlineBackfillRequestedEventPublisher, times(1)).publishEvent(captor.capture())
        assertThat(captor.firstValue.jobIds).containsExactly(novemberId, mayId)
    }

    @Test
    fun `enqueues asynchronously when threshold is exceeded`() {
        val context = fundPurchaseContext(tradeDate = LocalDate.of(2025, 1, 1))
        whenever(transactionRepository.findByHolding(context.holding.id!!, context.accountId))
            .thenReturn(listOf(requireNotNull(context.transaction)))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf("27717359000130")))
            .thenReturn(
                mapOf(
                    "27717359000130" to
                        FundMarketSnapshot(
                            fundCnpj = "27717359000130",
                            assetType = "FIXED_INCOME_FUND",
                            taxProfile = FundTaxProfile.COME_COTAS_LONG,
                            latestNav = null,
                        ),
                ),
            )
        whenever(jobRepository.findByHoldingAndCheckpointDate(any(), any())).thenReturn(null)
        whenever(jobRepository.save(any())).thenAnswer { invocation ->
            val job = invocation.arguments[0] as ComeCotasCheckpointJob
            job.copy(id = UUID.randomUUID())
        }

        handler.onRecord(context)

        verify(inlineBackfillRequestedEventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `non mutual fund holdings short circuit before resolveFund path`() {
        val context = fundPurchaseContext(assetType = AssetType.STOCK, tradeDate = LocalDate.of(2026, 5, 1))

        handler.onRecord(context)

        verify(fundMarketDataProvider, never()).getSnapshotsByCnpj(any())
        verify(transactionRepository, never()).findByHolding(any(), any())
        verify(jobRepository, never()).save(any())
    }

    @Test
    fun `provider lookup failure does not abort purchase backfill`() {
        val context =
            fundPurchaseContext(
                tradeDate = LocalDate.of(2025, 10, 22),
                comeCotasEnabled = null,
            )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf("27717359000130")))
            .doThrow(IllegalStateException("financial-data unavailable"))

        handler.onRecord(context)

        verify(transactionRepository, never()).findByHolding(any(), any())
        verify(jobRepository, never()).save(any())
        verify(inlineBackfillRequestedEventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `uses provider tax profile for cvm fixed income fund without manual override`() {
        val context =
            fundPurchaseContext(
                tradeDate = LocalDate.of(2025, 10, 22),
                comeCotasEnabled = null,
            )
        val novemberId = UUID.randomUUID()
        val mayId = UUID.randomUUID()
        whenever(transactionRepository.findByHolding(context.holding.id!!, context.accountId))
            .thenReturn(listOf(requireNotNull(context.transaction)))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf("27717359000130")))
            .thenReturn(
                mapOf(
                    "27717359000130" to
                        FundMarketSnapshot(
                            fundCnpj = "27717359000130",
                            assetType = "FIXED_INCOME_FUND",
                            taxProfile = FundTaxProfile.COME_COTAS_LONG,
                            latestNav = null,
                        ),
                ),
            )
        whenever(jobRepository.findByHoldingAndCheckpointDate(any(), any())).thenReturn(null)
        whenever(jobRepository.save(any())).thenAnswer {
            val job = it.arguments[0] as ComeCotasCheckpointJob
            val jobId =
                when (job.checkpointDate.monthValue) {
                    11 -> novemberId
                    5 -> mayId
                    else -> UUID.randomUUID()
                }
            job.copy(id = jobId)
        }

        handler.onRecord(context)

        val captor = argumentCaptor<ComeCotasInlineBackfillRequestedEvent>()
        verify(inlineBackfillRequestedEventPublisher, times(1)).publishEvent(captor.capture())
        assertThat(captor.firstValue.jobIds).containsExactly(novemberId, mayId)
    }

    @Test
    fun `manual override promotes variable income profile into eligible come cotas backfill`() {
        val context =
            fundPurchaseContext(
                tradeDate = LocalDate.of(2025, 10, 22),
                comeCotasEnabled = true,
            )
        val novemberId = UUID.randomUUID()
        val mayId = UUID.randomUUID()
        whenever(transactionRepository.findByHolding(context.holding.id!!, context.accountId))
            .thenReturn(listOf(requireNotNull(context.transaction)))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf("27717359000130")))
            .thenReturn(
                mapOf(
                    "27717359000130" to
                        FundMarketSnapshot(
                            fundCnpj = "27717359000130",
                            assetType = "EQUITY_FUND",
                            taxProfile = FundTaxProfile.VARIABLE_INCOME_15,
                            latestNav = null,
                        ),
                ),
            )
        whenever(jobRepository.findByHoldingAndCheckpointDate(any(), any())).thenReturn(null)
        whenever(jobRepository.save(any())).thenAnswer {
            val job = it.arguments[0] as ComeCotasCheckpointJob
            val jobId =
                when (job.checkpointDate.monthValue) {
                    11 -> novemberId
                    5 -> mayId
                    else -> UUID.randomUUID()
                }
            job.copy(id = jobId)
        }

        handler.onRecord(context)

        val captor = argumentCaptor<ComeCotasInlineBackfillRequestedEvent>()
        verify(inlineBackfillRequestedEventPublisher, times(1)).publishEvent(captor.capture())
        assertThat(captor.firstValue.jobIds).containsExactly(novemberId, mayId)
    }

    @Test
    fun `variable income fund without override stays ineligible for backfill`() {
        val context =
            fundPurchaseContext(
                tradeDate = LocalDate.of(2025, 10, 22),
                comeCotasEnabled = null,
            )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf("27717359000130")))
            .thenReturn(
                mapOf(
                    "27717359000130" to
                        FundMarketSnapshot(
                            fundCnpj = "27717359000130",
                            assetType = "EQUITY_FUND",
                            taxProfile = FundTaxProfile.VARIABLE_INCOME_15,
                            latestNav = null,
                        ),
                ),
            )

        handler.onRecord(context)

        verify(transactionRepository, never()).findByHolding(any(), any())
        verify(jobRepository, never()).save(any())
        verify(inlineBackfillRequestedEventPublisher, never()).publishEvent(any())
    }

    private fun fundPurchaseContext(
        assetType: AssetType = AssetType.MUTUAL_FUND,
        tradeDate: LocalDate,
        comeCotasEnabled: Boolean? = true,
    ): InvestmentTransactionContext {
        val accountId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val now = Instant.parse("2026-07-15T10:00:00Z")
        val holding =
            Holding(
                id = holdingId,
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100.00000000"),
                averageCost = BigDecimal("10.00000000"),
                comeCotasEnabled = comeCotasEnabled,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "Fund",
                assetType = assetType,
                fundCnpj = "27717359000130",
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val transaction =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = holding.financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100.00000000"),
                unitPrice = BigDecimal("10.00000000"),
                grossValue = BigDecimal("1000.00"),
                tradeDate = tradeDate,
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        return InvestmentTransactionContext(
            transaction = transaction,
            holding = holding,
            asset = asset,
            command = null,
            accountId = accountId,
            userId = UUID.randomUUID(),
        )
    }
}
