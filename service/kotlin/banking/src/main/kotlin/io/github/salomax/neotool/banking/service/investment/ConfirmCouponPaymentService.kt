package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.domain.investment.CouponFrequency
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ConfirmCouponPaymentCommand(
    val holdingId: UUID,
    val tradeDate: LocalDate,
    val grossValue: BigDecimal,
    val netValue: BigDecimal,
    val incomeTax: BigDecimal,
    val destinationAccountId: UUID,
    val pendingActionId: UUID? = null,
)

data class ConfirmCouponPaymentResult(
    val couponTransaction: InvestmentTransaction,
    val bankingTransaction: Transaction?,
    val pendingAction: AccountPendingAction?,
)

@Singleton
open class ConfirmCouponPaymentService(
    private val holdingRepository: HoldingRepository,
    private val investmentTransactionService: InvestmentTransactionService,
    private val pendingActionRepository: AccountPendingActionRepository,
    private val transactionRepository: TransactionRepository,
) {
    @Transactional
    open fun confirm(
        command: ConfirmCouponPaymentCommand,
        accountId: UUID,
        userId: UUID,
    ): ConfirmCouponPaymentResult {
        val holding =
            holdingRepository.findById(command.holdingId, accountId)
                ?: throw NoSuchElementException("Holding ${command.holdingId} not found")
        val holdingId = requireNotNull(holding.id) { "Holding id must not be null" }
        require(command.grossValue > BigDecimal.ZERO) { "grossValue must be > 0" }
        require(command.netValue >= BigDecimal.ZERO) { "netValue must be >= 0" }
        require(command.netValue <= command.grossValue) { "netValue must be <= grossValue" }
        require(holding.couponFrequency != null && holding.couponFrequency != CouponFrequency.AT_MATURITY) {
            "Holding $holdingId does not support coupon payments"
        }

        val pendingAction =
            command.pendingActionId?.let { actionId ->
                val pending =
                    pendingActionRepository.findById(actionId, accountId)
                        ?: throw NoSuchElementException("Pending action $actionId not found")
                require(pending.type == PendingActionType.COUPON_PAYMENT_DUE) {
                    "Pending action $actionId is not COUPON_PAYMENT_DUE"
                }
                require(pending.entityType == "HOLDING" && pending.entityId == holdingId) {
                    "Pending action $actionId does not belong to holding $holdingId"
                }
                pending
            }

        val recorded =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = holding.financialAccountId,
                        assetId = holding.assetId,
                        direction = InvestmentTransactionDirection.OUTFLOW,
                        type = InvestmentTransactionType.INTEREST_PAYMENT,
                        grossValue = command.grossValue,
                        netValue = command.netValue,
                        incomeTax = command.incomeTax,
                        tradeDate = command.tradeDate,
                        settlementDate = command.tradeDate,
                        source = InvestmentSource.MANUAL,
                        providerInvestmentId = holding.providerInvestmentId,
                        cashDestinationAccountId = command.destinationAccountId,
                    ),
                accountId = accountId,
                userId = userId,
            )

        val bankingTransaction =
            recorded.id?.let { investmentTxId ->
                transactionRepository.findByInvestmentTransactionIds(listOf(investmentTxId), accountId).firstOrNull()
            }

        val updatedPendingAction =
            pendingAction?.id?.let { pendingId ->
                pendingActionRepository.findById(pendingId, accountId)
            }

        return ConfirmCouponPaymentResult(
            couponTransaction = recorded,
            bankingTransaction = bankingTransaction,
            pendingAction = updatedPendingAction,
        )
    }
}
