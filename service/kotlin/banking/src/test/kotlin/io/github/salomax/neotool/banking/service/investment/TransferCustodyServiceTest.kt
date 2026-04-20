package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.common.error.NotFoundException
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("TransferCustodyService")
class TransferCustodyServiceTest {
    private lateinit var holdingRepository: HoldingRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var investmentTransactionService: InvestmentTransactionService
    private lateinit var service: TransferCustodyService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val sourceFinancialAccountId = UUID.randomUUID()
    private val destinationFinancialAccountId = UUID.randomUUID()
    private val sourceHoldingId = UUID.randomUUID()
    private val sourceAssetId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val sourceHolding =
        Holding(
            id = sourceHoldingId,
            financialAccountId = sourceFinancialAccountId,
            accountId = accountId,
            assetId = sourceAssetId,
            quantity = BigDecimal("100.000000"),
            averageCost = BigDecimal("15.25000000"),
            createdAt = now,
            updatedAt = now,
        )

    private val stockAsset =
        Asset(
            id = sourceAssetId,
            name = "PETR4",
            assetType = AssetType.STOCK,
            createdAt = now,
            updatedAt = now,
        )

    private val mutualFundAsset =
        Asset(
            id = sourceAssetId,
            name = "Fundo XP",
            assetType = AssetType.MUTUAL_FUND,
            createdAt = now,
            updatedAt = now,
        )

    private val outflowTransaction =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = sourceHoldingId,
            financialAccountId = sourceFinancialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = InvestmentTransactionType.CUSTODY_TRANSFER,
            quantity = BigDecimal("100.000000"),
            unitPrice = BigDecimal("15.25000000"),
            grossValue = BigDecimal("1525.000000000000"),
            netValue = BigDecimal("1525.000000000000"),
            tradeDate = today,
            settlementDate = today,
            source = InvestmentSource.MANUAL,
            createdAt = now,
            updatedAt = now,
        )

    private val inflowTransaction =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = UUID.randomUUID(),
            financialAccountId = destinationFinancialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.CUSTODY_TRANSFER,
            quantity = BigDecimal("100.000000"),
            unitPrice = BigDecimal("15.25000000"),
            grossValue = BigDecimal("1525.000000000000"),
            netValue = BigDecimal("1525.000000000000"),
            tradeDate = today,
            settlementDate = today,
            source = InvestmentSource.MANUAL,
            createdAt = now,
            updatedAt = now,
        )

    @BeforeEach
    fun setUp() {
        holdingRepository = mock()
        assetRepository = mock()
        financialAccountRepository = mock()
        investmentTransactionService = mock()
        service =
            TransferCustodyService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                investmentTransactionService = investmentTransactionService,
            )

        whenever(holdingRepository.findById(sourceHoldingId, accountId)).thenReturn(sourceHolding)
        whenever(
            financialAccountRepository.existsByIdAndAccountId(
                destinationFinancialAccountId,
                accountId,
            ),
        ).thenReturn(true)
        whenever(assetRepository.findById(sourceAssetId)).thenReturn(stockAsset)
        whenever(investmentTransactionService.record(any(), any(), any())).thenReturn(outflowTransaction, inflowTransaction)
    }

    @Test
    fun `records two custody legs for full transfer when quantity is null`() {
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = null,
                tradeDate = today,
            )

        val result = service.transfer(command, accountId, userId)

        assertThat(result.outflowTransaction).isEqualTo(outflowTransaction)
        assertThat(result.inflowTransaction).isEqualTo(inflowTransaction)

        val captured = captureRecordCommands()
        val expectedQty = sourceHolding.quantity

        assertLeg(
            command = captured[0],
            expectedDirection = InvestmentTransactionDirection.OUTFLOW,
            expectedFinancialAccountId = sourceFinancialAccountId,
            expectedType = InvestmentTransactionType.CUSTODY_TRANSFER,
            expectedQuantity = expectedQty,
        )
        assertLeg(
            command = captured[1],
            expectedDirection = InvestmentTransactionDirection.INFLOW,
            expectedFinancialAccountId = destinationFinancialAccountId,
            expectedType = InvestmentTransactionType.CUSTODY_TRANSFER,
            expectedQuantity = expectedQty,
        )
    }

    @Test
    fun `records two custody legs for partial transfer`() {
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("25.500000"),
                tradeDate = today,
            )

        service.transfer(command, accountId, userId)

        val captured = captureRecordCommands()
        val expectedQty = BigDecimal("25.500000")

        assertLeg(
            command = captured[0],
            expectedDirection = InvestmentTransactionDirection.OUTFLOW,
            expectedFinancialAccountId = sourceFinancialAccountId,
            expectedType = InvestmentTransactionType.CUSTODY_TRANSFER,
            expectedQuantity = expectedQty,
        )
        assertLeg(
            command = captured[1],
            expectedDirection = InvestmentTransactionDirection.INFLOW,
            expectedFinancialAccountId = destinationFinancialAccountId,
            expectedType = InvestmentTransactionType.CUSTODY_TRANSFER,
            expectedQuantity = expectedQty,
        )
    }

    @Test
    fun `uses QUOTA_TRANSFER for mutual fund holdings`() {
        whenever(assetRepository.findById(sourceAssetId)).thenReturn(mutualFundAsset)
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("10.000000"),
                tradeDate = today,
            )

        service.transfer(command, accountId, userId)

        val captured = captureRecordCommands()
        assertThat(captured[0].type).isEqualTo(InvestmentTransactionType.QUOTA_TRANSFER)
        assertThat(captured[1].type).isEqualTo(InvestmentTransactionType.QUOTA_TRANSFER)
    }

    @Test
    fun `uses CUSTODY_TRANSFER for non mutual fund holdings`() {
        whenever(assetRepository.findById(sourceAssetId)).thenReturn(stockAsset)
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("10.000000"),
                tradeDate = today,
            )

        service.transfer(command, accountId, userId)

        val captured = captureRecordCommands()
        assertThat(captured[0].type).isEqualTo(InvestmentTransactionType.CUSTODY_TRANSFER)
        assertThat(captured[1].type).isEqualTo(InvestmentTransactionType.CUSTODY_TRANSFER)
    }

    @Test
    fun `throws when destination financial account is same as source`() {
        whenever(financialAccountRepository.existsByIdAndAccountId(sourceFinancialAccountId, accountId)).thenReturn(true)
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = sourceFinancialAccountId,
                quantity = BigDecimal("10.000000"),
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_SAME_ACCOUNT_NOT_ALLOWED)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when transfer quantity exceeds source holding quantity`() {
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("100.000001"),
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_QUANTITY_EXCEEDS_SOURCE_HOLDING)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when destination account is not owned by account`() {
        whenever(
            financialAccountRepository.existsByIdAndAccountId(
                destinationFinancialAccountId,
                accountId,
            ),
        ).thenReturn(false)
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("10.000000"),
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_DESTINATION_ACCOUNT_INVALID)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws not found when source holding does not exist`() {
        whenever(holdingRepository.findById(sourceHoldingId, accountId)).thenReturn(null)
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("10.000000"),
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(NotFoundException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_HOLDING_NOT_FOUND)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws not found when source asset does not exist`() {
        whenever(assetRepository.findById(sourceAssetId)).thenReturn(null)
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("10.000000"),
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(NotFoundException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_ASSET_NOT_FOUND)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when transfer quantity is zero`() {
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal.ZERO,
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_QUANTITY_MUST_BE_POSITIVE)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when transfer quantity is negative`() {
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = BigDecimal("-1.000000"),
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_QUANTITY_MUST_BE_POSITIVE)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    @Test
    fun `throws when full transfer resolves to non positive source quantity`() {
        whenever(holdingRepository.findById(sourceHoldingId, accountId)).thenReturn(sourceHolding.copy(quantity = BigDecimal.ZERO))
        val command =
            TransferCustodyCommand(
                sourceHoldingId = sourceHoldingId,
                destinationFinancialAccountId = destinationFinancialAccountId,
                quantity = null,
                tradeDate = today,
            )

        assertThatThrownBy { service.transfer(command, accountId, userId) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(BankingErrorCode.TRANSFER_CUSTODY_QUANTITY_MUST_BE_POSITIVE)
            }

        verify(investmentTransactionService, never()).record(any(), any(), any())
    }

    private fun captureRecordCommands(): List<RecordInvestmentTransactionCommand> {
        val captor = argumentCaptor<RecordInvestmentTransactionCommand>()
        verify(investmentTransactionService, times(2)).record(captor.capture(), eq(accountId), eq(userId))
        return captor.allValues
    }

    private fun assertLeg(
        command: RecordInvestmentTransactionCommand,
        expectedDirection: InvestmentTransactionDirection,
        expectedFinancialAccountId: UUID,
        expectedType: InvestmentTransactionType,
        expectedQuantity: BigDecimal,
    ) {
        val expectedTransferValue = expectedQuantity * sourceHolding.averageCost

        assertThat(command.financialAccountId).isEqualTo(expectedFinancialAccountId)
        assertThat(command.assetId).isEqualTo(sourceAssetId)
        assertThat(command.direction).isEqualTo(expectedDirection)
        assertThat(command.type).isEqualTo(expectedType)
        assertThat(command.quantity).isEqualByComparingTo(expectedQuantity)
        assertThat(command.unitPrice).isEqualByComparingTo(sourceHolding.averageCost)
        assertThat(command.grossValue).isEqualByComparingTo(expectedTransferValue)
        assertThat(command.netValue).isNotNull
        assertThat(command.netValue!!).isEqualByComparingTo(expectedTransferValue)
        assertThat(command.tradeDate).isEqualTo(today)
        assertThat(command.settlementDate).isEqualTo(today)
        assertThat(command.source).isEqualTo(InvestmentSource.MANUAL)
    }
}
