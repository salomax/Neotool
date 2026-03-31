package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class HoldingMaturityConfirmService(
    private val holdingRepository: HoldingRepository,
    private val investmentTransactionService: InvestmentTransactionService,
    private val userPendingActionRepository: AccountPendingActionRepository,
    private val transactionService: TransactionService,
    private val financialAccountRepository: FinancialAccountRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    @Transactional
    open fun confirm(
        command: HoldingMaturityConfirmCommand,
        accountId: UUID,
        userId: UUID,
    ): HoldingMaturityConfirmResult {
        val holding =
            holdingRepository.findById(command.holdingId, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.HOLDING_MATURITY_INVALID_STATE,
                    field = "holdingId",
                    parameters = mapOf("reason" to "Holding not found"),
                )

        val today = clock()
        val maturity = holding.maturityDate
        if (maturity == null || maturity.isAfter(today)) {
            throw ValidationException(
                BankingErrorCode.HOLDING_MATURITY_INVALID_STATE,
                field = "maturityDate",
                parameters = mapOf("reason" to "Holding is not matured or has no maturity date"),
            )
        }

        if (holding.quantity <= BigDecimal.ZERO) {
            throw ValidationException(
                BankingErrorCode.HOLDING_MATURITY_INVALID_STATE,
                field = "quantity",
                parameters = mapOf("reason" to "Holding has no open quantity"),
            )
        }

        if (holding.closeReason != null) {
            throw ValidationException(
                BankingErrorCode.HOLDING_MATURITY_INVALID_STATE,
                field = "closeReason",
                parameters = mapOf("reason" to "Holding is already closed"),
            )
        }

        val holdingId = requireNotNull(holding.id)
        val pending =
            userPendingActionRepository.findPendingByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.INVESTMENT_MATURED,
            )
                ?: throw ValidationException(
                    BankingErrorCode.PENDING_ACTION_NOT_FOUND,
                    field = "holdingId",
                    parameters = mapOf("reason" to "No pending maturity action for this holding"),
                )

        if (pending.accountId != accountId) {
            throw ValidationException(
                BankingErrorCode.PENDING_ACTION_FORBIDDEN,
                field = "accountId",
                parameters = mapOf("reason" to "Pending action belongs to another account"),
            )
        }

        val recordCommand =
            RecordInvestmentTransactionCommand(
                financialAccountId = holding.financialAccountId,
                assetId = holding.assetId,
                direction = InvestmentTransactionDirection.OUTFLOW,
                type = InvestmentTransactionType.MATURITY,
                quantity = null,
                grossValue = command.grossValue,
                netValue = command.netValue,
                incomeTax = command.incomeTax,
                iof = command.iof,
                tradeDate = command.maturityDate,
                source = InvestmentSource.MANUAL,
                providerInvestmentId = holding.providerInvestmentId,
            )

        val maturityTx =
            investmentTransactionService.record(
                recordCommand,
                accountId = accountId,
                userId = userId,
            )

        val closed =
            holdingRepository.findById(command.holdingId, accountId)
                ?: error("Holding ${command.holdingId} missing after maturity record")
        val now = Instant.now()
        holdingRepository.update(
            closed.copy(
                closedAt = command.maturityDate,
                closeReason = command.closeReason,
                updatedAt = now,
            ),
        )

        val bankingTx =
            command.cashMovement?.let { cash ->
                val target =
                    financialAccountRepository.findById(cash.financialAccountId, accountId)
                        ?: throw ValidationException(
                            BankingErrorCode.HOLDING_MATURITY_INVALID_STATE,
                            field = "cashMovement.financialAccountId",
                            parameters = mapOf("reason" to "Financial account not found"),
                        )
                if (target.type != FinancialAccountType.BANK ||
                    target.subtype !in CASH_BANK_SUBTYPES
                ) {
                    throw ValidationException(
                        BankingErrorCode.HOLDING_MATURITY_INVALID_STATE,
                        field = "cashMovement.financialAccountId",
                        parameters = mapOf("reason" to "Credit must go to a checking/savings-style bank account"),
                    )
                }
                transactionService.create(
                    command =
                        CreateTransactionCommand(
                            financialAccountId = cash.financialAccountId,
                            creditDebitType = CreditDebitType.CREDIT,
                            type = TransactionType.INVESTMENT_RETURN,
                            amount = cash.amount,
                            date = cash.date,
                            description = "Resgate de investimento — vencimento",
                        ),
                    accountId = accountId,
                    userId = userId,
                )
            }

        val completedPending =
            userPendingActionRepository.update(
                pending.copy(
                    status = AccountPendingActionStatus.COMPLETED,
                    relatedTransactionId = requireNotNull(maturityTx.id),
                    completedAt = now,
                    updatedAt = now,
                ),
            )

        return HoldingMaturityConfirmResult(
            maturityTransaction = maturityTx,
            bankingTransaction = bankingTx,
            pendingAction = completedPending,
        )
    }

    private companion object {
        val CASH_BANK_SUBTYPES: Set<FinancialAccountSubtype> =
            setOf(
                FinancialAccountSubtype.CONTA_CORRENTE,
                FinancialAccountSubtype.CONTA_POUPANCA,
                FinancialAccountSubtype.CONTA_DIGITAL,
                FinancialAccountSubtype.CONTA_SALARIO,
            )
    }
}
