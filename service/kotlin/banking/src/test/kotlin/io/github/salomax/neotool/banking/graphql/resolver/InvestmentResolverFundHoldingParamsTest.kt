package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentResolver
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.model.dto.investment.UpdateFundHoldingParamsCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.service.investment.ConfirmCouponPaymentService
import io.github.salomax.neotool.banking.service.investment.ConfirmWithholdingTaxService
import io.github.salomax.neotool.banking.service.investment.InvestmentAssetService
import io.github.salomax.neotool.banking.service.investment.InvestmentCashService
import io.github.salomax.neotool.banking.service.investment.InvestmentHoldingOverrideService
import io.github.salomax.neotool.banking.service.investment.InvestmentPortfolioSummaryService
import io.github.salomax.neotool.banking.service.investment.InvestmentQueryService
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.service.investment.PortfolioFlowEventService
import io.github.salomax.neotool.banking.service.investment.PortfolioPerformanceQueryService
import io.github.salomax.neotool.banking.service.investment.PortfolioPerformanceSeriesService
import io.github.salomax.neotool.banking.service.investment.RecordAmortizationService
import io.github.salomax.neotool.banking.service.investment.RecordStockLendingService
import io.github.salomax.neotool.banking.service.investment.TransferCustodyService
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("InvestmentResolver fund holding params")
class InvestmentResolverFundHoldingParamsTest {
    private lateinit var investmentTransactionService: InvestmentTransactionService
    private lateinit var investmentAssetService: InvestmentAssetService
    private lateinit var investmentQueryService: InvestmentQueryService
    private lateinit var investmentHoldingOverrideService: InvestmentHoldingOverrideService
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
    private val holdingId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.of(2026, 7, 21)

    @BeforeEach
    fun setUp() {
        investmentTransactionService = mock()
        investmentAssetService = mock()
        investmentQueryService = mock()
        investmentHoldingOverrideService = mock()
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
                investmentHoldingOverrideService = investmentHoldingOverrideService,
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
    fun `maps administration fee when recording a fund transaction`() {
        whenever(investmentTransactionService.record(any(), eq(accountId), eq(userId))).thenReturn(transaction())
        val input =
            mapOf(
                "financialAccountId" to financialAccountId.toString(),
                "assetId" to assetId.toString(),
                "direction" to InvestmentTransactionDirection.INFLOW.name,
                "type" to InvestmentTransactionType.PURCHASE.name,
                "quantity" to 100,
                "unitPrice" to 10,
                "grossValue" to 1000,
                "tradeDate" to today.toString(),
                "settlementDate" to today.plusDays(1).toString(),
                "pricingMode" to HoldingPricingMode.MARKET_DATA.name,
                "administrationFeeAnnualPercent" to 1.75,
            )

        resolver.recordInvestmentTransaction(input, principal())

        val commandCaptor = argumentCaptor<RecordInvestmentTransactionCommand>()
        verify(investmentTransactionService).record(commandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(commandCaptor.firstValue.financialAccountId).isEqualTo(financialAccountId)
        assertThat(commandCaptor.firstValue.assetId).isEqualTo(assetId)
        assertThat(commandCaptor.firstValue.pricingMode).isEqualTo(HoldingPricingMode.MARKET_DATA)
        assertThat(commandCaptor.firstValue.administrationFeeAnnualPercent).isEqualByComparingTo("1.75")
    }

    @Test
    fun `preserves explicit null overrides in update fund holding params`() {
        val expectedHolding = mock<InvestmentHoldingDTO>()
        whenever(investmentQueryService.getHolding(holdingId, accountId)).thenReturn(expectedHolding)
        val input =
            mapOf(
                "holdingId" to holdingId.toString(),
                "administrationFeeAnnualPercent" to null,
                "comeCotasEnabled" to null,
            )

        val result = resolver.updateFundHoldingParams(input, principal())

        val commandCaptor = argumentCaptor<UpdateFundHoldingParamsCommand>()
        verify(investmentHoldingOverrideService).updateFundHoldingParams(commandCaptor.capture(), eq(accountId))
        assertThat(commandCaptor.firstValue.holdingId).isEqualTo(holdingId)
        assertThat(commandCaptor.firstValue.administrationFeeAnnualPercent).isNull()
        assertThat(commandCaptor.firstValue.hasAdministrationFeeAnnualPercent).isTrue()
        assertThat(commandCaptor.firstValue.comeCotasEnabled).isNull()
        assertThat(commandCaptor.firstValue.hasComeCotasEnabled).isTrue()
        assertThat(result).isSameAs(expectedHolding)
    }

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    private fun transaction() =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = BigDecimal("100"),
            unitPrice = BigDecimal("10"),
            grossValue = BigDecimal("1000"),
            netValue = BigDecimal("1000"),
            tradeDate = today,
            settlementDate = today.plusDays(1),
            source = InvestmentSource.MANUAL,
            createdAt = now,
            updatedAt = now,
        )
}
