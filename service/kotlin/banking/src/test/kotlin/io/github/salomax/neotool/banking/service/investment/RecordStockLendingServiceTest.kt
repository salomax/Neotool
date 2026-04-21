package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.model.dto.investment.RecordStockLendingCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.common.error.ValidationException
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

@DisplayName("RecordStockLendingService")
class RecordStockLendingServiceTest {
    private lateinit var holdingRepository: HoldingRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var investmentTransactionService: InvestmentTransactionService
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var service: RecordStockLendingService

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
            quantity = BigDecimal("500"),
            averageCost = BigDecimal("25.00"),
            createdAt = now,
            updatedAt = now,
        )

    private val lendableAsset =
        Asset(
            id = assetId,
            name = "ITUB4",
            assetType = AssetType.STOCK,
            createdAt = now,
            updatedAt = now,
        )

    private val recordedLendingTx =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.STOCK_LENDING,
            grossValue = BigDecimal("62.50"),
            netValue = BigDecimal("53.13"),
            incomeTax = BigDecimal("9.37"),
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
            amount = BigDecimal("53.13"),
            date = today,
            description = "Aluguel B3",
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
            RecordStockLendingService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                investmentTransactionService = investmentTransactionService,
                transactionRepository = transactionRepository,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(lendableAsset)
        whenever(investmentTransactionService.record(any(), any(), any())).thenReturn(recordedLendingTx)
        whenever(
            transactionRepository.findByInvestmentTransactionIds(
                listOf(recordedLendingTx.id!!),
                accountId,
            ),
        ).thenReturn(listOf(cashTx))
    }

    @Test
    fun `returns banking transaction when destination account is provided`() {
        val command =
            RecordStockLendingCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("62.50"),
                netValue = BigDecimal("53.13"),
                incomeTax = BigDecimal("9.37"),
                destinationAccountId = destinationAccountId,
            )

        val result = service.confirm(command, accountId, userId)

        assertThat(result.lendingTransaction).isEqualTo(recordedLendingTx)
        assertThat(result.bankingTransaction).isEqualTo(cashTx)

        val recordedCommandCaptor = argumentCaptor<RecordInvestmentTransactionCommand>()
        verify(investmentTransactionService).record(recordedCommandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(recordedCommandCaptor.firstValue.direction).isEqualTo(InvestmentTransactionDirection.INFLOW)
        assertThat(recordedCommandCaptor.firstValue.type).isEqualTo(InvestmentTransactionType.STOCK_LENDING)
    }

    @Test
    fun `returns null banking transaction when destination account is absent`() {
        val command =
            RecordStockLendingCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("62.50"),
                netValue = BigDecimal("53.13"),
                incomeTax = BigDecimal("9.37"),
                destinationAccountId = null,
            )

        val result = service.confirm(command, accountId, userId)

        assertThat(result.lendingTransaction).isEqualTo(recordedLendingTx)
        assertThat(result.bankingTransaction).isNull()

        val recordedCommandCaptor = argumentCaptor<RecordInvestmentTransactionCommand>()
        verify(investmentTransactionService).record(recordedCommandCaptor.capture(), eq(accountId), eq(userId))
        assertThat(recordedCommandCaptor.firstValue.cashDestinationAccountId).isNull()
        verify(transactionRepository, never()).findByInvestmentTransactionIds(any(), any())
    }

    @Test
    fun `throws when asset type is not eligible for stock lending`() {
        whenever(assetRepository.findById(assetId)).thenReturn(lendableAsset.copy(assetType = AssetType.MUTUAL_FUND))
        val command =
            RecordStockLendingCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("62.50"),
                netValue = BigDecimal("53.13"),
                incomeTax = BigDecimal("9.37"),
                destinationAccountId = destinationAccountId,
            )

        assertThatThrownBy { service.confirm(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.RECORD_STOCK_LENDING_ASSET_TYPE_NOT_SUPPORTED)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when gross value is zero or negative`() {
        val command =
            RecordStockLendingCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal.ZERO,
                netValue = BigDecimal.ZERO,
                incomeTax = BigDecimal.ZERO,
                destinationAccountId = destinationAccountId,
            )

        assertThatThrownBy { service.confirm(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.RECORD_STOCK_LENDING_GROSS_VALUE_MUST_BE_POSITIVE)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when net value exceeds gross value`() {
        val command =
            RecordStockLendingCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("62.50"),
                netValue = BigDecimal("62.51"),
                incomeTax = BigDecimal.ZERO,
                destinationAccountId = destinationAccountId,
            )

        assertThatThrownBy { service.confirm(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.RECORD_STOCK_LENDING_NET_VALUE_EXCEEDS_GROSS_VALUE)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws validation when income tax is negative`() {
        val command =
            RecordStockLendingCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("62.50"),
                netValue = BigDecimal("53.13"),
                incomeTax = BigDecimal("-0.01"),
                destinationAccountId = destinationAccountId,
            )

        assertThatThrownBy { service.confirm(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.RECORD_STOCK_LENDING_INCOME_TAX_MUST_BE_NON_NEGATIVE)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }
}
