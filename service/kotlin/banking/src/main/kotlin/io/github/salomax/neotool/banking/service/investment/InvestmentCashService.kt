package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.domain.transaction.UpdateTransactionCommand
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class InvestmentCashTransactionType {
    DEPOSIT,
    WITHDRAWAL,
    ADJUSTMENT,
}

/**
 * Manual cash movements on investment/benefit custodian accounts (no linked [investment transaction][io.github.salomax.neotool.banking.domain.transaction.Transaction.investmentTransactionId]).
 */
@Singleton
open class InvestmentCashService(
    private val financialAccountRepository: FinancialAccountRepository,
    private val transactionCategoryEntityRepository: TransactionCategoryEntityRepository,
    private val transactionService: TransactionService,
    private val accountPreferencesService: AccountPreferencesService,
    private val transactionRepository: TransactionRepository,
    private val pendingActionRepository: AccountPendingActionRepository,
    private val portfolioFlowEventService: PortfolioFlowEventService,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    open fun createManualCashTransaction(
        financialAccountId: UUID,
        type: InvestmentCashTransactionType,
        amount: BigDecimal,
        date: LocalDate,
        description: String?,
        accountId: UUID,
        userId: UUID,
        peerFinancialAccountId: UUID? = null,
        registerAsBalanceAdjustment: Boolean = false,
    ): Transaction {
        val custodian =
            financialAccountRepository.findById(financialAccountId, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_INVALID_FINANCIAL_ACCOUNT,
                    field = "financialAccountId",
                    parameters = mapOf("reason" to "Financial account not found"),
                )
        if (custodian.type != FinancialAccountType.INVESTMENT && custodian.type != FinancialAccountType.BENEFIT) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_INVALID_FINANCIAL_ACCOUNT,
                field = "financialAccountId",
            )
        }

        val today = LocalDate.now()
        if (date.isAfter(today)) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_DATE_MUST_NOT_BE_IN_FUTURE,
                field = "date",
                parameters = mapOf("maxDate" to today.toString()),
            )
        }

        val descTrimmed = description?.trim()?.takeIf { it.isNotEmpty() }
        val statusDefault = accountPreferencesService.get(accountId).defaultTransactionStatus

        if (registerAsBalanceAdjustment && peerFinancialAccountId != null) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_PEER_NOT_ALLOWED_FOR_ADJUSTMENT,
                field = "peerFinancialAccountId",
            )
        }

        if (registerAsBalanceAdjustment) {
            if (type == InvestmentCashTransactionType.ADJUSTMENT) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "registerAsBalanceAdjustment",
                    parameters = mapOf("reason" to "Cannot combine with type ADJUSTMENT"),
                )
            }
            if (descTrimmed == null) {
                throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_ADJUSTMENT_REQUIRES_DESCRIPTION,
                    field = "description",
                )
            }
            requirePositiveAmount(amount, "amount")
            val signed =
                when (type) {
                    InvestmentCashTransactionType.DEPOSIT -> amount
                    InvestmentCashTransactionType.WITHDRAWAL -> amount.negate()
                    InvestmentCashTransactionType.ADJUSTMENT -> amount
                }
            val adjustment =
                createBalanceAdjustment(
                    financialAccountId = financialAccountId,
                    signedAmount = signed,
                    date = date,
                    description = descTrimmed,
                    accountId = accountId,
                    userId = userId,
                    statusDefault = statusDefault,
                )
            syncPortfolioFlowForManualCash(financialAccountId, accountId, type, adjustment)
            return adjustment
        }

        if (peerFinancialAccountId != null) {
            if (type == InvestmentCashTransactionType.ADJUSTMENT) {
                throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_PEER_NOT_ALLOWED_FOR_ADJUSTMENT,
                    field = "peerFinancialAccountId",
                )
            }
            if (peerFinancialAccountId == financialAccountId) {
                throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_INVALID_PEER_ACCOUNT,
                    field = "peerFinancialAccountId",
                    parameters = mapOf("reason" to "Peer must differ from custodian account"),
                )
            }
            val peer = requireEligiblePeerAccount(peerFinancialAccountId, accountId)
            requirePositiveAmount(amount, "amount")
            val peerTransaction =
                when (type) {
                    InvestmentCashTransactionType.DEPOSIT ->
                    createDepositWithPeer(
                        custodianId = financialAccountId,
                        peer = peer,
                        amount = amount,
                        date = date,
                        description = descTrimmed,
                        accountId = accountId,
                        userId = userId,
                        statusDefault = statusDefault,
                    )
                    InvestmentCashTransactionType.WITHDRAWAL ->
                    createWithdrawalWithPeer(
                        custodianId = financialAccountId,
                        peer = peer,
                        amount = amount,
                        date = date,
                        description = descTrimmed,
                        accountId = accountId,
                        userId = userId,
                        statusDefault = statusDefault,
                    )
                    InvestmentCashTransactionType.ADJUSTMENT -> error("unreachable")
                }
            syncPortfolioFlowForManualCash(financialAccountId, accountId, type, peerTransaction)
            return peerTransaction
        }

        val savedTransaction =
            when (type) {
                InvestmentCashTransactionType.DEPOSIT -> {
                    requirePositiveAmount(amount, "amount")
                    val categoryId = requireTransferReceivedCategoryId()
                    transactionService.create(
                        CreateTransactionCommand(
                            financialAccountId = financialAccountId,
                            creditDebitType = CreditDebitType.CREDIT,
                            type = TransactionType.TRANSFER,
                            amount = amount,
                            date = date,
                            description = descTrimmed ?: "Aporte",
                            categoryId = categoryId,
                            transactionSource = TransactionSource.MANUAL,
                            investmentTransactionId = null,
                            skipRuleEngine = true,
                            status = statusDefault,
                        ),
                        accountId = accountId,
                        userId = userId,
                    )
                }
                InvestmentCashTransactionType.WITHDRAWAL -> {
                    requirePositiveAmount(amount, "amount")
                    val categoryId = requireTransferSentCategoryId()
                    transactionService.create(
                        CreateTransactionCommand(
                            financialAccountId = financialAccountId,
                            creditDebitType = CreditDebitType.DEBIT,
                            type = TransactionType.TRANSFER,
                            amount = amount,
                            date = date,
                            description = descTrimmed ?: "Saque",
                            categoryId = categoryId,
                            transactionSource = TransactionSource.MANUAL,
                            investmentTransactionId = null,
                            skipRuleEngine = true,
                            status = statusDefault,
                        ),
                        accountId = accountId,
                        userId = userId,
                    )
                }
                InvestmentCashTransactionType.ADJUSTMENT -> {
                    if (amount.signum() == 0) {
                        throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = "amount",
                            parameters = mapOf("reason" to "Amount must be non-zero"),
                        )
                    }
                    if (descTrimmed == null) {
                        throw ValidationException(
                            BankingErrorCode.INVESTMENT_MANUAL_CASH_ADJUSTMENT_REQUIRES_DESCRIPTION,
                            field = "description",
                        )
                    }
                    createBalanceAdjustment(
                        financialAccountId = financialAccountId,
                        signedAmount = amount,
                        date = date,
                        description = descTrimmed,
                        accountId = accountId,
                        userId = userId,
                        statusDefault = statusDefault,
                    )
                }
            }

        syncPortfolioFlowForManualCash(financialAccountId, accountId, type, savedTransaction)

        return savedTransaction
    }

    @Transactional
    open fun updateManualCashLedgerTransaction(
        transactionId: UUID,
        amount: BigDecimal,
        date: LocalDate,
        description: String?,
        accountId: UUID,
        userId: UUID,
    ): Transaction {
        val tx =
            transactionRepository.findById(transactionId, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_FOUND,
                    field = "id",
                )
        requireEditableCustodianManualCashLedgerRow(tx, accountId)

        val today = LocalDate.now()
        if (date.isAfter(today)) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_DATE_MUST_NOT_BE_IN_FUTURE,
                field = "date",
                parameters = mapOf("maxDate" to today.toString()),
            )
        }
        requirePositiveAmount(amount, "amount")

        val previousDate = tx.date
        val custodianFinancialAccountId = tx.financialAccountId
        val resolvedDescription =
            description?.trim()?.takeIf { it.isNotEmpty() } ?: tx.description

        val peer = resolveLinkedPeerTransaction(tx, accountId)

        val cmd =
            UpdateTransactionCommand(
                amount = amount,
                date = date,
                description = resolvedDescription,
            )
        val saved = transactionService.update(transactionId, cmd, accountId, userId)

        if (peer != null) {
            transactionService.update(
                requireNotNull(peer.id),
                UpdateTransactionCommand(amount = amount, date = date),
                accountId,
                userId,
            )
        }

        resyncPortfolioFlowAfterCustodianCashLedgerUpdate(
            previousDate = previousDate,
            updated = saved,
            custodianFinancialAccountId = custodianFinancialAccountId,
            accountId = accountId,
        )
        return saved
    }

    @Transactional
    open fun deleteManualCashLedgerTransaction(
        transactionId: UUID,
        accountId: UUID,
    ): Boolean {
        val tx =
            transactionRepository.findById(transactionId, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_FOUND,
                    field = "id",
                )
        requireEditableCustodianManualCashLedgerRow(tx, accountId)

        val peer = resolveLinkedPeerTransaction(tx, accountId)
        val custodianFa = tx.financialAccountId
        val fromDate = tx.date

        if (tx.type == TransactionType.TRANSFER) {
            portfolioFlowEventService.deleteEventBySourceReference(
                transactionId,
                PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
            )
            // The flow event may have been keyed to the peer (custodian) leg instead of the
            // initiating (bank) leg — e.g. when the user deletes from the bank-account side.
            peer?.id?.let { peerId ->
                portfolioFlowEventService.deleteEventBySourceReference(
                    peerId,
                    PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
                )
            }
        }

        portfolioFlowEventService.requestRecalculation(
            financialAccountId = custodianFa,
            accountId = accountId,
            recalculateFrom = fromDate,
            triggeredBy = "cash_ledger_delete:$transactionId",
        )

        transactionService.delete(transactionId, accountId)
        if (peer != null) {
            transactionService.delete(requireNotNull(peer.id), accountId)
        }
        return true
    }

    private fun requireEditableCustodianManualCashLedgerRow(
        tx: Transaction,
        accountId: UUID,
    ) {
        if (tx.source != TransactionSource.MANUAL) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_EDITABLE,
                field = "id",
            )
        }
        if (tx.investmentTransactionId != null) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_EDITABLE,
                field = "id",
                parameters = mapOf("reason" to "Linked to an investment trade"),
            )
        }
        val fa =
            financialAccountRepository.findById(tx.financialAccountId, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_EDITABLE,
                    field = "id",
                )
        if (fa.type != FinancialAccountType.INVESTMENT && fa.type != FinancialAccountType.BENEFIT) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_EDITABLE,
                field = "id",
            )
        }
        if (tx.type != TransactionType.TRANSFER &&
            tx.type != TransactionType.BALANCE_ADJUSTMENT &&
            tx.type != TransactionType.INVESTMENT_RETURN
        ) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_EDITABLE,
                field = "id",
            )
        }
    }

    private fun resolveLinkedPeerTransaction(
        tx: Transaction,
        accountId: UUID,
    ): Transaction? {
        val directPeerId = tx.transferPeerTransactionId
        if (directPeerId != null) {
            return transactionRepository.findById(directPeerId, accountId)
        }
        val id = tx.id ?: return null
        return transactionRepository.findByTransferPeerTransactionId(id, accountId)
    }

    private fun resyncPortfolioFlowAfterCustodianCashLedgerUpdate(
        previousDate: LocalDate,
        updated: Transaction,
        custodianFinancialAccountId: UUID,
        accountId: UUID,
    ) {
        val recalcFrom = if (previousDate.isBefore(updated.date)) previousDate else updated.date
        when (updated.type) {
            TransactionType.BALANCE_ADJUSTMENT ->
                portfolioFlowEventService.requestRecalculation(
                    financialAccountId = custodianFinancialAccountId,
                    accountId = accountId,
                    recalculateFrom = recalcFrom,
                    triggeredBy = "cash_ledger_update:${updated.id}",
                )
            TransactionType.TRANSFER ->
                when (updated.creditDebitType) {
                    CreditDebitType.CREDIT ->
                        portfolioFlowEventService.recordEvent(
                            financialAccountId = custodianFinancialAccountId,
                            accountId = accountId,
                            eventDate = updated.date,
                            eventType = PortfolioFlowEventType.DEPOSIT,
                            grossAmount = updated.amount,
                            sourceReferenceId = updated.id,
                            sourceReferenceType = PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
                        )
                    CreditDebitType.DEBIT ->
                        portfolioFlowEventService.recordEvent(
                            financialAccountId = custodianFinancialAccountId,
                            accountId = accountId,
                            eventDate = updated.date,
                            eventType = PortfolioFlowEventType.WITHDRAWAL,
                            grossAmount = updated.amount,
                            sourceReferenceId = updated.id,
                            sourceReferenceType = PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
                        )
                }
            else ->
                portfolioFlowEventService.requestRecalculation(
                    financialAccountId = custodianFinancialAccountId,
                    accountId = accountId,
                    recalculateFrom = recalcFrom,
                    triggeredBy = "cash_ledger_update:${updated.id}",
                )
        }
    }

    private fun syncPortfolioFlowForManualCash(
        financialAccountId: UUID,
        accountId: UUID,
        type: InvestmentCashTransactionType,
        transaction: Transaction,
    ) {
        when (type) {
            InvestmentCashTransactionType.DEPOSIT ->
                portfolioFlowEventService.recordEvent(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    eventDate = transaction.date,
                    eventType = PortfolioFlowEventType.DEPOSIT,
                    grossAmount = transaction.amount,
                    sourceReferenceId = transaction.id,
                    sourceReferenceType = PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
                )
            InvestmentCashTransactionType.WITHDRAWAL ->
                portfolioFlowEventService.recordEvent(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    eventDate = transaction.date,
                    eventType = PortfolioFlowEventType.WITHDRAWAL,
                    grossAmount = transaction.amount,
                    sourceReferenceId = transaction.id,
                    sourceReferenceType = PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION,
                )
            InvestmentCashTransactionType.ADJUSTMENT ->
                portfolioFlowEventService.requestRecalculation(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    recalculateFrom = transaction.date,
                    triggeredBy = "cash_adjustment:${transaction.id}",
                )
        }
    }

    private fun createBalanceAdjustment(
        financialAccountId: UUID,
        signedAmount: BigDecimal,
        date: LocalDate,
        description: String,
        accountId: UUID,
        userId: UUID,
        statusDefault: TransactionStatus,
    ): Transaction {
        val absAmount = signedAmount.abs()
        val creditDebit =
            if (signedAmount.signum() > 0) {
                CreditDebitType.CREDIT
            } else {
                CreditDebitType.DEBIT
            }
        return transactionService.create(
            CreateTransactionCommand(
                financialAccountId = financialAccountId,
                creditDebitType = creditDebit,
                type = TransactionType.BALANCE_ADJUSTMENT,
                amount = absAmount,
                date = date,
                description = description,
                categoryId = null,
                transactionSource = TransactionSource.MANUAL,
                investmentTransactionId = null,
                skipRuleEngine = true,
                status = statusDefault,
            ),
            accountId = accountId,
            userId = userId,
        )
    }

    private fun createDepositWithPeer(
        custodianId: UUID,
        peer: FinancialAccount,
        amount: BigDecimal,
        date: LocalDate,
        description: String?,
        accountId: UUID,
        userId: UUID,
        statusDefault: TransactionStatus,
    ): Transaction {
        val sentCategoryId = requireTransferSentCategoryId()
        val receivedCategoryId = requireTransferReceivedCategoryId()
        val bankDebit =
            transactionService.create(
                CreateTransactionCommand(
                    financialAccountId = peer.id!!,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    date = date,
                    description = description ?: "Transferência para investimentos",
                    categoryId = sentCategoryId,
                    transactionSource = TransactionSource.MANUAL,
                    investmentTransactionId = null,
                    skipRuleEngine = true,
                    status = statusDefault,
                ),
                accountId = accountId,
                userId = userId,
            )
        val bankDebitId = requireNotNull(bankDebit.id)
        val inv =
            transactionService.create(
                CreateTransactionCommand(
                    financialAccountId = custodianId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    date = date,
                    description = description ?: "Aporte",
                    categoryId = receivedCategoryId,
                    transactionSource = TransactionSource.MANUAL,
                    investmentTransactionId = null,
                    skipRuleEngine = true,
                    status = statusDefault,
                    transferPeerTransactionId = bankDebitId,
                ),
                accountId = accountId,
                userId = userId,
            )
        maybeEnqueueNegativeBalanceWarning(
            accountId = accountId,
            peerFinancialAccountId = peer.id!!,
            peerName = peer.name,
            relatedDebitTransactionId = bankDebitId,
        )
        return inv
    }

    private fun createWithdrawalWithPeer(
        custodianId: UUID,
        peer: FinancialAccount,
        amount: BigDecimal,
        date: LocalDate,
        description: String?,
        accountId: UUID,
        userId: UUID,
        statusDefault: TransactionStatus,
    ): Transaction {
        val sentCategoryId = requireTransferSentCategoryId()
        val receivedCategoryId = requireTransferReceivedCategoryId()
        val invDebit =
            transactionService.create(
                CreateTransactionCommand(
                    financialAccountId = custodianId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    date = date,
                    description = description ?: "Saque",
                    categoryId = sentCategoryId,
                    transactionSource = TransactionSource.MANUAL,
                    investmentTransactionId = null,
                    skipRuleEngine = true,
                    status = statusDefault,
                ),
                accountId = accountId,
                userId = userId,
            )
        val invDebitId = requireNotNull(invDebit.id)
        transactionService.create(
            CreateTransactionCommand(
                financialAccountId = peer.id!!,
                creditDebitType = CreditDebitType.CREDIT,
                type = TransactionType.TRANSFER,
                amount = amount,
                date = date,
                description = description ?: "Transferência recebida — resgate investimento",
                categoryId = receivedCategoryId,
                transactionSource = TransactionSource.MANUAL,
                investmentTransactionId = null,
                skipRuleEngine = true,
                status = statusDefault,
                transferPeerTransactionId = invDebitId,
            ),
            accountId = accountId,
            userId = userId,
        )
        return invDebit
    }

    private fun maybeEnqueueNegativeBalanceWarning(
        accountId: UUID,
        peerFinancialAccountId: UUID,
        peerName: String,
        relatedDebitTransactionId: UUID,
    ) {
        val balance = transactionRepository.sumLedgerBalance(accountId, listOf(peerFinancialAccountId))
        if (balance >= BigDecimal.ZERO) return
        val now = Instant.now()
        pendingActionRepository.save(
            AccountPendingAction(
                accountId = accountId,
                type = PendingActionType.BANK_ACCOUNT_NEGATIVE_BALANCE,
                severity = PendingActionSeverity.WARNING,
                status = AccountPendingActionStatus.PENDING,
                entityType = "FINANCIAL_ACCOUNT",
                entityId = peerFinancialAccountId,
                relatedTransactionId = relatedDebitTransactionId,
                payload =
                    mapOf(
                        "financialAccountId" to peerFinancialAccountId.toString(),
                        "financialAccountName" to peerName,
                        "ledgerBalance" to balance.toPlainString(),
                    ),
                visibleFrom = now,
                notifiedAt = null,
                dismissedAt = null,
                completedAt = null,
                cancelledAt = null,
                expiresAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun requireEligiblePeerAccount(
        peerFinancialAccountId: UUID,
        accountId: UUID,
    ): FinancialAccount {
        val peer =
            financialAccountRepository.findById(peerFinancialAccountId, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.INVESTMENT_MANUAL_CASH_INVALID_PEER_ACCOUNT,
                    field = "peerFinancialAccountId",
                    parameters = mapOf("reason" to "Financial account not found"),
                )
        if (!isEligiblePeerAccount(peer)) {
            throw ValidationException(
                BankingErrorCode.INVESTMENT_MANUAL_CASH_INVALID_PEER_ACCOUNT,
                field = "peerFinancialAccountId",
            )
        }
        return peer
    }

    private fun isEligiblePeerAccount(peer: FinancialAccount): Boolean {
        if (peer.type == FinancialAccountType.BANK && peer.subtype in PEER_BANK_SUBTYPES) {
            return true
        }
        if (peer.type == FinancialAccountType.INVESTMENT && peer.subtype == FinancialAccountSubtype.BROKERAGE) {
            return true
        }
        return false
    }

    private fun requirePositiveAmount(
        amount: BigDecimal,
        field: String,
    ) {
        if (amount <= BigDecimal.ZERO) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("reason" to "Amount must be greater than zero"),
            )
        }
    }

    private fun requireTransferReceivedCategoryId(): UUID {
        val id =
            transactionCategoryEntityRepository.findSystemSubcategoryId(
                "Transferência recebida",
                "Entre contas próprias",
            )
        if (id == null) {
            logger.warn { "System category missing: Transferência recebida > Entre contas próprias" }
            throw ValidationException(BankingErrorCode.INVESTMENT_MANUAL_CASH_SYSTEM_CATEGORY_MISSING)
        }
        return id
    }

    private fun requireTransferSentCategoryId(): UUID {
        val id =
            transactionCategoryEntityRepository.findSystemSubcategoryId(
                "Transferência enviada",
                "Entre contas próprias",
            )
        if (id == null) {
            logger.warn { "System category missing: Transferência enviada > Entre contas próprias" }
            throw ValidationException(BankingErrorCode.INVESTMENT_MANUAL_CASH_SYSTEM_CATEGORY_MISSING)
        }
        return id
    }

    private companion object {
        private const val PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION = "BANKING_TRANSACTION"
        val PEER_BANK_SUBTYPES: Set<FinancialAccountSubtype> =
            setOf(
                FinancialAccountSubtype.CONTA_CORRENTE,
                FinancialAccountSubtype.CONTA_POUPANCA,
                FinancialAccountSubtype.CONTA_DIGITAL,
                FinancialAccountSubtype.CONTA_SALARIO,
                FinancialAccountSubtype.CONTA_CONJUNTA,
            )
    }
}
