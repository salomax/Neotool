package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.graphql.investment.InvestmentResolver
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.service.investment.ConfirmCouponPaymentService
import io.github.salomax.neotool.banking.service.investment.ConfirmWithholdingTaxService
import io.github.salomax.neotool.banking.service.investment.InvestmentAssetService
import io.github.salomax.neotool.banking.service.investment.InvestmentCashService
import io.github.salomax.neotool.banking.service.investment.InvestmentPortfolioSummaryService
import io.github.salomax.neotool.banking.service.investment.InvestmentQueryService
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.service.investment.PortfolioFlowEventService
import io.github.salomax.neotool.banking.service.investment.PortfolioPerformanceQueryService
import io.github.salomax.neotool.banking.service.investment.PortfolioPerformanceSeriesService
import io.github.salomax.neotool.banking.service.investment.RecordAmortizationService
import io.github.salomax.neotool.banking.service.investment.RecordStockLendingService
import io.github.salomax.neotool.banking.model.dto.investment.TransferCustodyCommand
import io.github.salomax.neotool.banking.model.dto.investment.TransferCustodyResult
import io.github.salomax.neotool.banking.service.investment.TransferCustodyService
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("InvestmentResolver transferCustody")
class InvestmentResolverTransferCustodyTest {
    private lateinit var investmentTransactionService: InvestmentTransactionService
    private lateinit var investmentAssetService: InvestmentAssetService
    private lateinit var investmentQueryService: InvestmentQueryService
    private lateinit var investmentPortfolioSummaryService: InvestmentPortfolioSummaryService
    private lateinit var portfolioPerformanceSeriesService: PortfolioPerformanceSeriesService
    private lateinit var assetRepository: AssetRepository
    private lateinit var investmentCashService: InvestmentCashService
    private lateinit var confirmCouponPaymentService: ConfirmCouponPaymentService
    private lateinit var confirmWithholdingTaxService: ConfirmWithholdingTaxService
    private lateinit var recordAmortizationService: RecordAmortizationService
    private lateinit var recordStockLendingService: RecordStockLendingService
    private lateinit var transferCustodyService: TransferCustodyService
    private lateinit var transactionResolver: TransactionResolver
    private lateinit var portfolioFlowEventService: PortfolioFlowEventService
    private lateinit var portfolioPerformanceQueryService: PortfolioPerformanceQueryService
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var resolver: InvestmentResolver

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val sourceHoldingId = UUID.randomUUID()
    private val sourceFinancialAccountId = UUID.randomUUID()
    private val destinationFinancialAccountId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.of(2026, 4, 20)

    @BeforeEach
    fun setUp() {
        investmentTransactionService = mock()
        investmentAssetService = mock()
        investmentQueryService = mock()
        investmentPortfolioSummaryService = mock()
        portfolioPerformanceSeriesService = mock()
        assetRepository = mock()
        investmentCashService = mock()
        confirmCouponPaymentService = mock()
        confirmWithholdingTaxService = mock()
        recordAmortizationService = mock()
        recordStockLendingService = mock()
        transferCustodyService = mock()
        transactionResolver = mock()
        portfolioFlowEventService = mock()
        portfolioPerformanceQueryService = mock()
        financialAccountRepository = mock()
        resolver =
            InvestmentResolver(
                investmentTransactionService = investmentTransactionService,
                investmentAssetService = investmentAssetService,
                investmentQueryService = investmentQueryService,
                investmentPortfolioSummaryService = investmentPortfolioSummaryService,
                portfolioPerformanceSeriesService = portfolioPerformanceSeriesService,
                assetRepository = assetRepository,
                investmentCashService = investmentCashService,
                confirmCouponPaymentService = confirmCouponPaymentService,
                confirmWithholdingTaxService = confirmWithholdingTaxService,
                recordAmortizationService = recordAmortizationService,
                recordStockLendingService = recordStockLendingService,
                transferCustodyService = transferCustodyService,
                transactionResolver = transactionResolver,
                portfolioFlowEventService = portfolioFlowEventService,
                portfolioPerformanceQueryService = portfolioPerformanceQueryService,
                financialAccountRepository = financialAccountRepository,
            )
    }

    @Test
    fun `maps input into command and returns DTO for partial transfer`() {
        whenever(transferCustodyService.transfer(any(), eq(accountId), eq(userId))).thenReturn(
            TransferCustodyResult(
                outflowTransaction = transaction(direction = InvestmentTransactionDirection.OUTFLOW, quantity = BigDecimal("12.5")),
                inflowTransaction = transaction(direction = InvestmentTransactionDirection.INFLOW, quantity = BigDecimal("12.5")),
            ),
        )
        val input =
            mapOf(
                "sourceHoldingId" to sourceHoldingId.toString(),
                "destinationFinancialAccountId" to destinationFinancialAccountId.toString(),
                "quantity" to 12.5,
                "tradeDate" to "2026-04-20",
            )

        val result = resolver.transferCustody(input, principal())

        val commandCaptor = argumentCaptor<TransferCustodyCommand>()
        verify(transferCustodyService).transfer(commandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(commandCaptor.firstValue.sourceHoldingId).isEqualTo(sourceHoldingId)
        assertThat(commandCaptor.firstValue.destinationFinancialAccountId).isEqualTo(destinationFinancialAccountId)
        assertThat(commandCaptor.firstValue.quantity).isEqualByComparingTo(BigDecimal("12.5"))
        assertThat(commandCaptor.firstValue.tradeDate).isEqualTo(today)
        assertThat(result.outflowTransaction.quantity).isEqualTo(12.5)
        assertThat(result.inflowTransaction.quantity).isEqualTo(12.5)
    }

    @Test
    fun `keeps quantity null for full transfer when input omits quantity`() {
        whenever(transferCustodyService.transfer(any(), eq(accountId), eq(userId))).thenReturn(
            TransferCustodyResult(
                outflowTransaction = transaction(direction = InvestmentTransactionDirection.OUTFLOW, quantity = BigDecimal("100")),
                inflowTransaction = transaction(direction = InvestmentTransactionDirection.INFLOW, quantity = BigDecimal("100")),
            ),
        )
        val input =
            mapOf(
                "sourceHoldingId" to sourceHoldingId.toString(),
                "destinationFinancialAccountId" to destinationFinancialAccountId.toString(),
                "tradeDate" to today,
            )

        resolver.transferCustody(input, principal())

        val commandCaptor = argumentCaptor<TransferCustodyCommand>()
        verify(transferCustodyService).transfer(commandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(commandCaptor.firstValue.quantity).isNull()
        assertThat(commandCaptor.firstValue.tradeDate).isEqualTo(today)
    }

    @Test
    fun `throws when principal has no user id`() {
        val input =
            mapOf(
                "sourceHoldingId" to sourceHoldingId.toString(),
                "destinationFinancialAccountId" to destinationFinancialAccountId.toString(),
                "tradeDate" to "2026-04-20",
            )

        val exception =
            assertThrows<IllegalArgumentException> {
                resolver.transferCustody(input, principal(userId = null))
            }

        assertThat(exception.message).isEqualTo("No user in token")
        verify(transferCustodyService, never()).transfer(any(), any(), any())
    }

    private fun principal(userId: UUID? = this.userId) =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    private fun transaction(
        direction: InvestmentTransactionDirection,
        quantity: BigDecimal,
    ) = InvestmentTransaction(
        id = UUID.randomUUID(),
        holdingId = sourceHoldingId,
        financialAccountId =
            if (direction == InvestmentTransactionDirection.OUTFLOW) {
                sourceFinancialAccountId
            } else {
                destinationFinancialAccountId
            },
        accountId = accountId,
        createdByUserId = userId,
        direction = direction,
        type = InvestmentTransactionType.CUSTODY_TRANSFER,
        quantity = quantity,
        unitPrice = BigDecimal("15.25"),
        grossValue = quantity * BigDecimal("15.25"),
        netValue = quantity * BigDecimal("15.25"),
        tradeDate = today,
        settlementDate = today,
        source = InvestmentSource.MANUAL,
        createdAt = now,
        updatedAt = now,
    )
}
