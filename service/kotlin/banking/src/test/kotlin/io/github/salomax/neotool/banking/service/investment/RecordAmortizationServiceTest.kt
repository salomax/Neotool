package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.model.dto.investment.RecordAmortizationCommand
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
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

@DisplayName("RecordAmortizationService")
class RecordAmortizationServiceTest {
    private lateinit var holdingRepository: HoldingRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var investmentTransactionService: InvestmentTransactionService
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var service: RecordAmortizationService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val destinationAccountId = UUID.randomUUID()
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
            quantity = BigDecimal("10"),
            averageCost = BigDecimal("1000.00"),
            createdAt = now,
            updatedAt = now,
        )

    private val amortizableAsset =
        Asset(
            id = assetId,
            name = "Debênture XP",
            assetType = AssetType.DEBENTURES,
            createdAt = now,
            updatedAt = now,
        )

    private val recordedAmortizationTx =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = InvestmentTransactionType.AMORTIZATION,
            grossValue = BigDecimal("1000.00"),
            netValue = BigDecimal("850.00"),
            incomeTax = BigDecimal("150.00"),
            iof = BigDecimal.ZERO,
            tradeDate = today,
            settlementDate = today,
            source = InvestmentSource.MANUAL,
        )

    private val cashTx =
        Transaction(
            id = UUID.randomUUID(),
            financialAccountId = destinationAccountId,
            accountId = accountId,
            createdByUserId = userId,
            creditDebitType = CreditDebitType.CREDIT,
            type = TransactionType.INVESTMENT_RETURN,
            amount = BigDecimal("850.00"),
            date = today,
            description = "Amortização",
            source = TransactionSource.MANUAL,
            status = TransactionStatus.POSTED,
            createdAt = now,
            updatedAt = now,
        )

    @BeforeEach
    fun setUp() {
        holdingRepository = mock()
        assetRepository = mock()
        investmentTransactionService = mock()
        transactionRepository = mock()
        service =
            RecordAmortizationService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                investmentTransactionService = investmentTransactionService,
                transactionRepository = transactionRepository,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(amortizableAsset)
        whenever(investmentTransactionService.record(any(), any(), any())).thenReturn(recordedAmortizationTx)
        whenever(
            transactionRepository.findByInvestmentTransactionIds(
                listOf(recordedAmortizationTx.id!!),
                accountId,
            ),
        ).thenReturn(listOf(cashTx))
    }

    @Test
    fun `returns banking transaction when destination account is provided`() {
        val command =
            RecordAmortizationCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("1000.00"),
                netValue = BigDecimal("850.00"),
                incomeTax = BigDecimal("150.00"),
                destinationAccountId = destinationAccountId,
            )

        val result = service.confirm(command, accountId, userId)

        assertThat(result.amortizationTransaction).isEqualTo(recordedAmortizationTx)
        assertThat(result.bankingTransaction).isEqualTo(cashTx)
    }

    @Test
    fun `returns null banking transaction when destination account is absent`() {
        val command =
            RecordAmortizationCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("1000.00"),
                netValue = BigDecimal("850.00"),
                incomeTax = BigDecimal("150.00"),
                destinationAccountId = null,
            )

        val result = service.confirm(command, accountId, userId)

        assertThat(result.amortizationTransaction).isEqualTo(recordedAmortizationTx)
        assertThat(result.bankingTransaction).isNull()

        val recordedCommandCaptor = argumentCaptor<RecordInvestmentTransactionCommand>()
        verify(investmentTransactionService).record(recordedCommandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(recordedCommandCaptor.firstValue.cashDestinationAccountId).isNull()
        verify(transactionRepository, never()).findByInvestmentTransactionIds(any(), any())
    }

    @Test
    fun `throws when asset type is not eligible for amortization`() {
        whenever(assetRepository.findById(assetId)).thenReturn(amortizableAsset.copy(assetType = AssetType.STOCK))
        val command =
            RecordAmortizationCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("1000.00"),
                netValue = BigDecimal("850.00"),
                incomeTax = BigDecimal("150.00"),
                destinationAccountId = destinationAccountId,
            )

        val ex =
            assertThrows<ValidationException> {
                service.confirm(command, accountId, userId)
            }
        assertThat(ex.errorCode).isEqualTo(CommonErrorCode.INVALID_STATE)
        assertThat(ex.field).isEqualTo("holdingId")

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when gross value is zero or negative`() {
        val command =
            RecordAmortizationCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal.ZERO,
                netValue = BigDecimal.ZERO,
                incomeTax = BigDecimal.ZERO,
                destinationAccountId = destinationAccountId,
            )

        val ex =
            assertThrows<ValidationException> {
                service.confirm(command, accountId, userId)
            }
        assertThat(ex.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT)
        assertThat(ex.field).isEqualTo("grossValue")

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when net value exceeds gross value`() {
        val command =
            RecordAmortizationCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("1000.00"),
                netValue = BigDecimal("1000.01"),
                incomeTax = BigDecimal.ZERO,
                destinationAccountId = destinationAccountId,
            )

        val ex =
            assertThrows<ValidationException> {
                service.confirm(command, accountId, userId)
            }
        assertThat(ex.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT)
        assertThat(ex.field).isEqualTo("netValue")

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }
}
