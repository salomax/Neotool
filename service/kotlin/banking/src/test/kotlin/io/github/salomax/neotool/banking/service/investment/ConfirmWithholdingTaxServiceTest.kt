package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxCommand
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("ConfirmWithholdingTaxService")
class ConfirmWithholdingTaxServiceTest {
    private lateinit var holdingRepository: HoldingRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var investmentTransactionService: InvestmentTransactionService
    private lateinit var service: ConfirmWithholdingTaxService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val holdingId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val holding =
        Holding(
            id = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            quantity = BigDecimal("100.000000"),
            averageCost = BigDecimal("12.34000000"),
            createdAt = now,
            updatedAt = now,
        )

    private val mutualFundAsset =
        Asset(
            id = assetId,
            name = "Fundo XP CDI",
            assetType = AssetType.MUTUAL_FUND,
            createdAt = now,
            updatedAt = now,
        )

    private val recordedWithholdingTaxTx =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = InvestmentTransactionType.WITHHOLDING_TAX,
            quantity = BigDecimal("10.000000"),
            unitPrice = BigDecimal("15.00000000"),
            grossValue = BigDecimal("150.00"),
            netValue = BigDecimal("150.00"),
            tradeDate = today,
            settlementDate = today,
            source = InvestmentSource.MANUAL,
        )

    @BeforeEach
    fun setUp() {
        holdingRepository = mock()
        assetRepository = mock()
        investmentTransactionService = mock()
        service =
            ConfirmWithholdingTaxService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                investmentTransactionService = investmentTransactionService,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(mutualFundAsset)
        whenever(investmentTransactionService.record(any(), any(), any())).thenReturn(recordedWithholdingTaxTx)
    }

    @Test
    fun `records withholding tax without creating cash destination`() {
        val command =
            ConfirmWithholdingTaxCommand(
                holdingId = holdingId,
                tradeDate = today,
                irValue = BigDecimal("150.00"),
                quotasDeducted = BigDecimal("10.000000"),
            )

        val result = service.confirm(command, accountId, userId)

        assertThat(result.withholdingTaxTransaction).isEqualTo(recordedWithholdingTaxTx)

        val recordedCommandCaptor = argumentCaptor<RecordInvestmentTransactionCommand>()
        verify(investmentTransactionService).record(recordedCommandCaptor.capture(), eq(accountId), eq(userId))
        val recordedCommand = recordedCommandCaptor.firstValue
        assertThat(recordedCommand.financialAccountId).isEqualTo(financialAccountId)
        assertThat(recordedCommand.assetId).isEqualTo(assetId)
        assertThat(recordedCommand.direction).isEqualTo(InvestmentTransactionDirection.OUTFLOW)
        assertThat(recordedCommand.type).isEqualTo(InvestmentTransactionType.WITHHOLDING_TAX)
        assertThat(recordedCommand.grossValue).isEqualByComparingTo("150.00")
        assertThat(recordedCommand.netValue).isEqualByComparingTo("150.00")
        assertThat(recordedCommand.quantity).isEqualByComparingTo("10.000000")
        assertThat(recordedCommand.unitPrice).isEqualByComparingTo("15.00000000")
        assertThat(recordedCommand.tradeDate).isEqualTo(today)
        assertThat(recordedCommand.settlementDate).isEqualTo(today)
        assertThat(recordedCommand.source).isEqualTo(InvestmentSource.MANUAL)
        assertThat(recordedCommand.cashDestinationAccountId).isNull()
    }

    @Test
    fun `propagates system source and deterministic provider transaction id`() {
        val command =
            ConfirmWithholdingTaxCommand(
                holdingId = holdingId,
                tradeDate = today,
                irValue = BigDecimal("150.00"),
                quotasDeducted = BigDecimal("10.000000"),
                source = InvestmentSource.SYSTEM,
                providerTransactionId = "comecotas:$holdingId:$today",
            )

        service.confirm(command, accountId, userId)

        val recordedCommandCaptor = argumentCaptor<RecordInvestmentTransactionCommand>()
        verify(investmentTransactionService).record(recordedCommandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(recordedCommandCaptor.firstValue.source).isEqualTo(InvestmentSource.SYSTEM)
        assertThat(recordedCommandCaptor.firstValue.providerTransactionId).isEqualTo("comecotas:$holdingId:$today")
    }

    @Test
    fun `throws when quotas deducted exceeds holding quantity`() {
        val command =
            ConfirmWithholdingTaxCommand(
                holdingId = holdingId,
                tradeDate = today,
                irValue = BigDecimal("10.00"),
                quotasDeducted = BigDecimal("100.000001"),
            )

        assertThatThrownBy { service.confirm(command, accountId, userId) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("exceeds current holding quantity")

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when holding asset is not mutual fund`() {
        whenever(assetRepository.findById(assetId)).thenReturn(mutualFundAsset.copy(assetType = AssetType.STOCK))
        val command =
            ConfirmWithholdingTaxCommand(
                holdingId = holdingId,
                tradeDate = today,
                irValue = BigDecimal("10.00"),
                quotasDeducted = BigDecimal("1"),
            )

        assertThatThrownBy { service.confirm(command, accountId, userId) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("WITHHOLDING_TAX is only valid for MUTUAL_FUND holdings")

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when irValue is zero or negative`() {
        val command =
            ConfirmWithholdingTaxCommand(
                holdingId = holdingId,
                tradeDate = today,
                irValue = BigDecimal.ZERO,
                quotasDeducted = BigDecimal("1"),
            )

        assertThatThrownBy { service.confirm(command, accountId, userId) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("irValue must be > 0")

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }
}
