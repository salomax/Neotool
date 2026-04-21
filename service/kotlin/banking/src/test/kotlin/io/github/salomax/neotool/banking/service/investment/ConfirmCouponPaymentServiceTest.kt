package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.model.dto.investment.ConfirmCouponPaymentCommand
import io.github.salomax.neotool.banking.domain.investment.CouponFrequency
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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

@DisplayName("ConfirmCouponPaymentService")
class ConfirmCouponPaymentServiceTest {

    private lateinit var holdingRepository: HoldingRepository
    private lateinit var investmentTransactionService: InvestmentTransactionService
    private lateinit var pendingActionRepository: AccountPendingActionRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var service: ConfirmCouponPaymentService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val holdingId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val semiAnnualHolding = Holding(
        id = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        assetId = assetId,
        quantity = BigDecimal("1"),
        averageCost = BigDecimal("1000.00"),
        couponFrequency = CouponFrequency.SEMI_ANNUAL,
        createdAt = now,
        updatedAt = now,
    )

    private val recordedCouponTx = InvestmentTransaction(
        id = UUID.randomUUID(),
        holdingId = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        direction = InvestmentTransactionDirection.OUTFLOW,
        type = InvestmentTransactionType.INTEREST_PAYMENT,
        grossValue = BigDecimal("100.00"),
        netValue = BigDecimal("85.00"),
        incomeTax = BigDecimal("15.00"),
        tradeDate = today,
        source = InvestmentSource.MANUAL,
    )

    private val cashTx = Transaction(
        id = UUID.randomUUID(),
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        creditDebitType = CreditDebitType.CREDIT,
        type = TransactionType.INVESTMENT_RETURN,
        amount = BigDecimal("85.00"),
        date = today,
        description = "Coupon payment",
        source = TransactionSource.MANUAL,
        status = TransactionStatus.POSTED,
        createdAt = now,
        updatedAt = now,
    )

    @BeforeEach
    fun setUp() {
        holdingRepository = mock()
        investmentTransactionService = mock()
        pendingActionRepository = mock()
        transactionRepository = mock()
        service = ConfirmCouponPaymentService(
            holdingRepository = holdingRepository,
            investmentTransactionService = investmentTransactionService,
            pendingActionRepository = pendingActionRepository,
            transactionRepository = transactionRepository,
        )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(semiAnnualHolding)
        whenever(investmentTransactionService.record(any(), any(), any())).thenReturn(recordedCouponTx)
        whenever(
            transactionRepository.findByInvestmentTransactionIds(
                listOf(recordedCouponTx.id!!), accountId
            )
        ).thenReturn(listOf(cashTx))
    }

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        @Test
        fun `records coupon transaction and returns banking transaction`() {
            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
            )

            val result = service.confirm(command, accountId, userId)

            assertThat(result.couponTransaction).isEqualTo(recordedCouponTx)
            assertThat(result.bankingTransaction).isEqualTo(cashTx)
            assertThat(result.pendingAction).isNull()
        }

        @Test
        fun `resolves pending action when pendingActionId is provided`() {
            val pendingActionId = UUID.randomUUID()
            val pendingAction = AccountPendingAction(
                id = pendingActionId,
                accountId = accountId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
                severity = PendingActionSeverity.ACTION_REQUIRED,
                status = AccountPendingActionStatus.COMPLETED,
                entityType = "HOLDING",
                entityId = holdingId,
                relatedTransactionId = null,
                payload = emptyMap(),
                visibleFrom = now,
                notifiedAt = null,
                dismissedAt = null,
                completedAt = now,
                cancelledAt = null,
                expiresAt = null,
                createdAt = now,
                updatedAt = now,
            )
            whenever(pendingActionRepository.findById(pendingActionId, accountId)).thenReturn(pendingAction)

            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
                pendingActionId = pendingActionId,
            )

            val result = service.confirm(command, accountId, userId)

            assertThat(result.pendingAction).isEqualTo(pendingAction)
        }
    }

    @Nested
    @DisplayName("validation")
    inner class Validation {

        @Test
        fun `throws when holding not found`() {
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(null)

            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
            )

            assertThatThrownBy { service.confirm(command, accountId, userId) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining(holdingId.toString())
        }

        @Test
        fun `throws when grossValue is zero`() {
            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal.ZERO,
                netValue = BigDecimal.ZERO,
                incomeTax = BigDecimal.ZERO,
                destinationAccountId = financialAccountId,
            )

            assertThatThrownBy { service.confirm(command, accountId, userId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("grossValue must be > 0")
        }

        @Test
        fun `throws when netValue exceeds grossValue`() {
            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("110.00"),
                incomeTax = BigDecimal.ZERO,
                destinationAccountId = financialAccountId,
            )

            assertThatThrownBy { service.confirm(command, accountId, userId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("netValue must be <= grossValue")
        }

        @Test
        fun `throws when holding has AT_MATURITY coupon frequency`() {
            val atMaturityHolding = semiAnnualHolding.copy(couponFrequency = CouponFrequency.AT_MATURITY)
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(atMaturityHolding)

            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
            )

            assertThatThrownBy { service.confirm(command, accountId, userId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("does not support coupon payments")
        }

        @Test
        fun `throws when holding has null coupon frequency`() {
            val noCouponHolding = semiAnnualHolding.copy(couponFrequency = null)
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(noCouponHolding)

            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
            )

            assertThatThrownBy { service.confirm(command, accountId, userId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("does not support coupon payments")
        }

        @Test
        fun `throws when pending action not found`() {
            val pendingActionId = UUID.randomUUID()
            whenever(pendingActionRepository.findById(pendingActionId, accountId)).thenReturn(null)

            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
                pendingActionId = pendingActionId,
            )

            assertThatThrownBy { service.confirm(command, accountId, userId) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining(pendingActionId.toString())
        }

        @Test
        fun `throws when pending action type is not COUPON_PAYMENT_DUE`() {
            val pendingActionId = UUID.randomUUID()
            val wrongTypePendingAction = AccountPendingAction(
                id = pendingActionId,
                accountId = accountId,
                type = PendingActionType.INVESTMENT_MATURED,
                severity = PendingActionSeverity.ACTION_REQUIRED,
                status = AccountPendingActionStatus.PENDING,
                entityType = "HOLDING",
                entityId = holdingId,
                relatedTransactionId = null,
                payload = emptyMap(),
                visibleFrom = now,
                notifiedAt = null,
                dismissedAt = null,
                completedAt = null,
                cancelledAt = null,
                expiresAt = null,
                createdAt = now,
                updatedAt = now,
            )
            whenever(pendingActionRepository.findById(pendingActionId, accountId)).thenReturn(wrongTypePendingAction)

            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
                pendingActionId = pendingActionId,
            )

            assertThatThrownBy { service.confirm(command, accountId, userId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("COUPON_PAYMENT_DUE")
        }

        @Test
        fun `does not touch pending action repository when no pendingActionId`() {
            val command = ConfirmCouponPaymentCommand(
                holdingId = holdingId,
                tradeDate = today,
                grossValue = BigDecimal("100.00"),
                netValue = BigDecimal("85.00"),
                incomeTax = BigDecimal("15.00"),
                destinationAccountId = financialAccountId,
            )

            service.confirm(command, accountId, userId)

            verify(pendingActionRepository, never()).findById(any(), any())
        }
    }
}
