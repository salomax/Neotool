package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.service.investment.ValuationSource
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.micronaut.context.event.ApplicationEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PortfolioFlowEventServiceTest {
    private val portfolioFlowEventRepository: PortfolioFlowEventRepository = mock()
    private val portfolioQuotaRepository: PortfolioQuotaRepository = mock()
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository = mock()
    private val investmentQueryService: InvestmentQueryService = mock()
    private val recalculationRequestedEventPublisher: ApplicationEventPublisher<PortfolioRecalculationRequestedEvent> = mock()

    private val service =
        PortfolioFlowEventService(
            portfolioFlowEventRepository = portfolioFlowEventRepository,
            portfolioQuotaRepository = portfolioQuotaRepository,
            portfolioRecalculationJobRepository = portfolioRecalculationJobRepository,
            investmentQueryService = investmentQueryService,
            recalculationRequestedEventPublisher = recalculationRequestedEventPublisher,
        )

    @Test
    fun `records deposit event using latest quota and creates recalculation job`() {
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val sourceReferenceId = UUID.randomUUID()
        val eventDate = LocalDate.of(2026, 4, 3)

        whenever(investmentQueryService.getHoldings(financialAccountId, accountId)).thenReturn(
            listOf(holding(financialAccountId, accountId, grossValue = 1200.0)),
        )
        whenever(investmentQueryService.getInvestmentCashBalance(financialAccountId, accountId)).thenReturn(BigDecimal("300.00"))
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = eventDate,
            ),
        ).thenReturn(quota(financialAccountId, eventDate.minusDays(1), "1.25000000"))
        whenever(portfolioRecalculationJobRepository.findPendingByAccount(accountId)).thenReturn(null)
        whenever(portfolioFlowEventRepository.save(any())).thenAnswer { it.arguments.first() }
        whenever(portfolioRecalculationJobRepository.save(any())).thenAnswer { inv ->
            val job = inv.arguments[0] as PortfolioRecalculationJob
            job.copy(id = job.id ?: UUID.randomUUID())
        }

        val saved =
            service.recordEvent(
                financialAccountId = financialAccountId,
                accountId = accountId,
                eventDate = eventDate,
                eventType = PortfolioFlowEventType.DEPOSIT,
                grossAmount = BigDecimal("200.00"),
                sourceReferenceId = sourceReferenceId,
                sourceReferenceType = "BANKING_TRANSACTION",
            )

        assertThat(saved.portfolioValueBefore).isEqualByComparingTo("1300.00")
        assertThat(saved.quotaValueAtEvent).isEqualByComparingTo("1.25000000")
        assertThat(saved.quotasTransacted).isEqualByComparingTo("160.00000000")
        verify(portfolioFlowEventRepository).deleteBySourceReference(sourceReferenceId, "BANKING_TRANSACTION")
        verify(portfolioRecalculationJobRepository).save(any())
        verify(recalculationRequestedEventPublisher).publishEvent(any())
    }

    @Test
    fun `updates existing pending recalculation job to earliest affected date`() {
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val existing =
            PortfolioRecalculationJob(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                status = PortfolioRecalculationStatus.PENDING,
                recalculateFrom = LocalDate.of(2026, 4, 10),
                triggeredBy = "old",
                createdAt = Instant.parse("2026-04-10T10:00:00Z"),
                updatedAt = Instant.parse("2026-04-10T10:00:00Z"),
            )
        whenever(portfolioRecalculationJobRepository.findPendingByAccount(accountId)).thenReturn(existing)
        whenever(portfolioRecalculationJobRepository.update(any())).thenAnswer { it.arguments.first() }

        val updated =
            service.requestRecalculation(
                financialAccountId = financialAccountId,
                accountId = accountId,
                recalculateFrom = LocalDate.of(2026, 4, 3),
                triggeredBy = "new-trigger",
            )

        assertThat(updated.recalculateFrom).isEqualTo(LocalDate.of(2026, 4, 3))
        assertThat(updated.triggeredBy).isEqualTo("new-trigger")
        verify(portfolioRecalculationJobRepository, never()).save(any())
        verify(recalculationRequestedEventPublisher).publishEvent(any())
    }

    @Test
    fun `deletes flow events by source reference`() {
        val sourceReferenceId = UUID.randomUUID()
        whenever(portfolioFlowEventRepository.deleteBySourceReference(sourceReferenceId, "INVESTMENT_TRANSACTION")).thenReturn(1)

        val deleted = service.deleteEventBySourceReference(sourceReferenceId, "INVESTMENT_TRANSACTION")

        assertThat(deleted).isEqualTo(1)
    }

    @Test
    fun `deleteEventsByRelatedHolding delegates to repository`() {
        val financialAccountId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        whenever(portfolioFlowEventRepository.deleteByFinancialAccountAndRelatedHoldingId(financialAccountId, holdingId)).thenReturn(2)

        val n = service.deleteEventsByRelatedHolding(financialAccountId, holdingId)

        assertThat(n).isEqualTo(2)
        verify(portfolioFlowEventRepository).deleteByFinancialAccountAndRelatedHoldingId(financialAccountId, holdingId)
    }

    private fun quota(
        financialAccountId: UUID,
        quotaDate: LocalDate,
        quotaValue: String,
    ) = InvestmentPortfolioQuota(
        financialAccountId = financialAccountId,
        dimensionType = PortfolioDimensionType.PORTFOLIO,
        dimensionKey = null,
        quotaDate = quotaDate,
        quotaValue = BigDecimal(quotaValue),
        totalQuotas = BigDecimal("100.00000000"),
        totalPortfolioValue = BigDecimal("1000.00"),
    )

    private fun holding(
        financialAccountId: UUID,
        accountId: UUID,
        grossValue: Double,
    ): InvestmentHoldingDTO {
        val now = Instant.parse("2026-04-03T12:00:00Z")
        return InvestmentHoldingDTO(
            id = UUID.randomUUID(),
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = UUID.randomUUID(),
            asset = null,
            financialAccount = null,
            quantity = 1.0,
            averageCost = 1.0,
            indexer = null,
            indexerPercentage = null,
            fixedAnnualRate = null,
            issueDate = null,
            maturityDate = null,
            gracePeriodDate = null,
            isTaxExempt = false,
            grossValue = grossValue,
            netValue = grossValue,
            incomeTax = null,
            iof = null,
            lastValuedAt = null,
            pricingMode = null,
            valuationSource = ValuationSource.MANUAL,
            valuationReferenceDate = null,
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
