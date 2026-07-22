package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.UpdateFundHoldingParamsCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InvestmentHoldingOverrideServiceTest {
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()
    private val investmentTransactionRepository: InvestmentTransactionRepository = mock()
    private val portfolioFlowEventService: PortfolioFlowEventService = mock()
    private val service =
        InvestmentHoldingOverrideService(
            holdingRepository,
            assetRepository,
            investmentTransactionRepository,
            portfolioFlowEventService,
        )

    @Test
    fun `persists come-cotas override on mutual fund holding`() {
        val now = Instant.parse("2026-07-15T12:00:00Z")
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal.ONE,
                averageCost = BigDecimal.ONE,
                createdAt = now,
                updatedAt = now,
            )
        whenever(holdingRepository.findById(holding.id!!, holding.accountId)).thenReturn(holding)
        whenever(assetRepository.findById(holding.assetId)).thenReturn(
            Asset(
                id = holding.assetId,
                name = "Fund",
                assetType = AssetType.MUTUAL_FUND,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            ),
        )
        whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }
        whenever(investmentTransactionRepository.findByHolding(holding.id!!, holding.accountId)).thenReturn(emptyList())

        val updated = service.setComeCotasEnabled(holding.id, holding.accountId, true)

        assertThat(updated.comeCotasEnabled).isTrue()
        verify(portfolioFlowEventService).requestRecalculation(
            holding.financialAccountId,
            holding.accountId,
            LocalDate.now(),
            "fund_holding_params:${holding.id}",
        )
    }

    @Test
    fun `updates administration fee and recalculates from earliest holding trade date`() {
        val now = Instant.parse("2026-07-15T12:00:00Z")
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal.ONE,
                averageCost = BigDecimal.ONE,
                createdAt = now,
                updatedAt = now,
            )
        whenever(holdingRepository.findById(holding.id!!, holding.accountId)).thenReturn(holding)
        whenever(assetRepository.findById(holding.assetId)).thenReturn(
            Asset(
                id = holding.assetId,
                name = "Fund",
                assetType = AssetType.MUTUAL_FUND,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            ),
        )
        whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }
        whenever(investmentTransactionRepository.findByHolding(holding.id, holding.accountId)).thenReturn(
            listOf(
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holding.id,
                    financialAccountId = holding.financialAccountId,
                    accountId = holding.accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    grossValue = BigDecimal.TEN,
                    tradeDate = LocalDate.of(2026, 1, 10),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )

        val updated =
            service.updateFundHoldingParams(
                UpdateFundHoldingParamsCommand(
                    holdingId = holding.id,
                    administrationFeeAnnualPercent = BigDecimal("1.75"),
                    hasAdministrationFeeAnnualPercent = true,
                    comeCotasEnabled = false,
                    hasComeCotasEnabled = true,
                ),
                holding.accountId,
            )

        assertThat(updated.administrationFeeAnnualPercent).isEqualByComparingTo("1.75")
        assertThat(updated.comeCotasEnabled).isFalse()
        verify(portfolioFlowEventService).requestRecalculation(
            holding.financialAccountId,
            holding.accountId,
            LocalDate.of(2026, 1, 10),
            "fund_holding_params:${holding.id}",
        )
    }
}
