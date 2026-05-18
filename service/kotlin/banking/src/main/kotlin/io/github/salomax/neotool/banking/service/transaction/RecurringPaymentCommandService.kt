package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.RecurringFrequency
import io.github.salomax.neotool.banking.domain.transaction.RecurringMatchingService
import io.github.salomax.neotool.banking.domain.transaction.RecurringPayment
import io.github.salomax.neotool.banking.domain.transaction.RecurringPaymentService
import io.github.salomax.neotool.banking.domain.transaction.RecurringPaymentStatus
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
class RecurringPaymentCommandService(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val recurringPaymentService: RecurringPaymentService,
    private val recurringMatchingService: RecurringMatchingService,
    private val transactionRepository: TransactionRepository,
    private val transactionService: TransactionService,
    private val pendingActionRepository: AccountPendingActionRepository,
) {
    fun listRecurringPayments(
        accountId: UUID,
        financialAccountId: UUID?,
    ): List<RecurringPayment> =
        if (financialAccountId == null) {
            recurringPaymentRepository.findAllByAccountId(accountId)
        } else {
            recurringPaymentRepository.findAllByFinancialAccountId(financialAccountId, accountId)
        }

    fun findRecurringPayment(
        accountId: UUID,
        recurringPaymentId: UUID,
    ): RecurringPayment? = recurringPaymentRepository.findById(recurringPaymentId, accountId)

    fun suggestRecurringForTransaction(
        accountId: UUID,
        transactionId: UUID,
    ): RecurringPayment? {
        val transaction = transactionRepository.findById(transactionId, accountId) ?: return null
        return recurringMatchingService.findBestMatch(transaction)
    }

    fun createRecurringPayment(
        accountId: UUID,
        input: CreateRecurringPaymentCommand,
    ): RecurringPayment {
        val now = Instant.now()
        val recurring =
            RecurringPayment(
                accountId = accountId,
                financialAccountId = input.financialAccountId,
                name = input.name,
                creditDebitType = input.creditDebitType,
                type = input.type,
                expectedAmount = input.expectedAmount,
                currencyCode = input.currencyCode,
                categoryId = input.categoryId,
                merchantId = input.merchantId,
                frequency = input.frequency,
                dayOfMonth = input.dayOfMonth,
                intervalDays = input.intervalDays,
                remindDaysBefore = input.remindDaysBefore,
                status = RecurringPaymentStatus.ACTIVE,
                nextDueDate = input.firstDueDate,
                lastPaidDate = null,
                notes = input.notes,
                createdAt = now,
                updatedAt = now,
            )
        recurringPaymentService.validateTemplate(recurring)
        return recurringPaymentRepository.save(recurring)
    }

    fun updateRecurringPayment(
        accountId: UUID,
        recurringPaymentId: UUID,
        input: UpdateRecurringPaymentCommand,
    ): RecurringPayment {
        val existing = requireRecurringPayment(accountId, recurringPaymentId)
        val updated =
            existing.copy(
                financialAccountId = if (input.financialAccountIdDefined) input.financialAccountId else existing.financialAccountId,
                name = input.name ?: existing.name,
                creditDebitType = input.creditDebitType ?: existing.creditDebitType,
                expectedAmount = if (input.expectedAmountDefined) input.expectedAmount else existing.expectedAmount,
                categoryId = if (input.categoryIdDefined) input.categoryId else existing.categoryId,
                merchantId = if (input.merchantIdDefined) input.merchantId else existing.merchantId,
                frequency = input.frequency ?: existing.frequency,
                dayOfMonth = if (input.dayOfMonthDefined) input.dayOfMonth else existing.dayOfMonth,
                intervalDays = if (input.intervalDaysDefined) input.intervalDays else existing.intervalDays,
                remindDaysBefore = input.remindDaysBefore ?: existing.remindDaysBefore,
                nextDueDate = input.nextDueDate ?: existing.nextDueDate,
                notes = if (input.notesDefined) input.notes else existing.notes,
                updatedAt = Instant.now(),
            )
        recurringPaymentService.validateTemplate(updated)
        return recurringPaymentRepository.update(updated)
    }

    fun pauseRecurringPayment(
        accountId: UUID,
        recurringPaymentId: UUID,
    ): RecurringPayment {
        val existing = requireRecurringPayment(accountId, recurringPaymentId)
        val updated = recurringPaymentRepository.update(existing.copy(status = RecurringPaymentStatus.PAUSED, updatedAt = Instant.now()))
        updated.id?.let(transactionRepository::deleteFutureByRecurringPaymentId)
        return updated
    }

    fun resumeRecurringPayment(
        accountId: UUID,
        recurringPaymentId: UUID,
    ): RecurringPayment {
        val existing = requireRecurringPayment(accountId, recurringPaymentId)
        return recurringPaymentRepository.update(
            existing.copy(
                status = RecurringPaymentStatus.ACTIVE,
                updatedAt = Instant.now(),
            ),
        )
    }

    fun deleteRecurringPayment(
        accountId: UUID,
        recurringPaymentId: UUID,
    ): Boolean {
        val existing = requireRecurringPayment(accountId, recurringPaymentId)
        val id = requireNotNull(existing.id)
        transactionRepository.deleteFutureByRecurringPaymentId(id)
        transactionRepository.clearRecurringPaymentId(id, accountId)
        pendingActionRepository.dismissOpenForEntity(
            entityType = "RecurringPayment",
            entityId = id,
            type = PendingActionType.RECURRING_PAYMENT_DUE,
        )
        recurringPaymentRepository.softDelete(id, accountId)
        return true
    }

    fun linkTransactionToRecurring(
        accountId: UUID,
        transactionId: UUID,
        recurringPaymentId: UUID,
    ): Transaction {
        val tx = transactionService.findById(transactionId, accountId)
        val recurring = requireRecurringPayment(accountId, recurringPaymentId)
        val resolvedRecurringId = recurring.id ?: throw IllegalStateException("Recurring payment without id")

        if (recurring.status != RecurringPaymentStatus.ACTIVE) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_STATE,
                field = "status",
                parameters = mapOf("reason" to "only ACTIVE recurring payments can be linked"),
            )
        }
        if (tx.status != TransactionStatus.POSTED) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_STATE,
                field = "status",
                parameters = mapOf("reason" to "only POSTED transactions can be linked"),
            )
        }
        if (tx.type == TransactionType.BALANCE_ADJUSTMENT) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "type",
                parameters = mapOf("reason" to "BALANCE_ADJUSTMENT cannot be linked to recurring payments"),
            )
        }
        if (tx.recurringPaymentId != null && tx.recurringPaymentId != resolvedRecurringId) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_STATE,
                field = "transactionId",
                parameters = mapOf("reason" to "transaction is already linked to another recurring payment"),
            )
        }

        transactionRepository.setRecurringPaymentId(
            transactionId = requireNotNull(tx.id),
            recurringPaymentId = resolvedRecurringId,
            accountId = accountId,
        )
        recurringPaymentService.confirmPayment(recurring, tx.date)
        pendingActionRepository.dismissOpenForEntity(
            entityType = "RecurringPayment",
            entityId = resolvedRecurringId,
            type = PendingActionType.RECURRING_PAYMENT_DUE,
        )
        transactionRepository.deleteFutureByRecurringAndDate(resolvedRecurringId, tx.date)
        return transactionService.findById(requireNotNull(tx.id), accountId)
    }

    fun confirmRecurringPayment(
        accountId: UUID,
        recurringPaymentId: UUID,
        paymentDate: LocalDate,
    ): RecurringPayment {
        val existing = requireRecurringPayment(accountId, recurringPaymentId)
        if (existing.status != RecurringPaymentStatus.ACTIVE) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_STATE,
                field = "status",
                parameters = mapOf("reason" to "only ACTIVE recurring payments can be confirmed"),
            )
        }
        val updated = recurringPaymentService.confirmPayment(existing, paymentDate)
        existing.id?.let { id ->
            pendingActionRepository.dismissOpenForEntity(
                entityType = "RecurringPayment",
                entityId = id,
                type = PendingActionType.RECURRING_PAYMENT_DUE,
            )
            transactionRepository.deleteFutureByRecurringAndDate(id, paymentDate)
        }
        return updated
    }

    fun createRecurringPaymentFromTransaction(
        accountId: UUID,
        transactionId: UUID,
        input: CreateRecurringPaymentFromTransactionCommand,
    ): RecurringPayment {
        val tx = transactionService.findById(transactionId, accountId)
        val now = Instant.now()
        val recurring =
            RecurringPayment(
                accountId = accountId,
                financialAccountId =
                    if (input.financialAccountIdOverrideDefined) {
                        input.financialAccountIdOverride
                    } else {
                        tx.financialAccountId
                    },
                name = input.name ?: tx.description,
                creditDebitType = tx.creditDebitType,
                type = input.type ?: tx.type,
                expectedAmount = input.expectedAmount ?: tx.amount,
                currencyCode = input.currencyCode ?: tx.currencyCode,
                categoryId = if (input.categoryIdDefined) input.categoryId else tx.categoryId,
                merchantId = if (input.merchantIdDefined) input.merchantId else tx.merchantId,
                frequency = input.frequency ?: RecurringFrequency.MONTHLY,
                dayOfMonth = input.dayOfMonth ?: tx.date.dayOfMonth,
                intervalDays = input.intervalDays,
                remindDaysBefore = input.remindDaysBefore ?: 3,
                status = RecurringPaymentStatus.ACTIVE,
                nextDueDate = input.firstDueDate ?: tx.date,
                notes = input.notes,
                createdAt = now,
                updatedAt = now,
            )
        recurringPaymentService.validateTemplate(recurring)
        val saved = recurringPaymentRepository.save(recurring)
        linkTransactionToRecurring(accountId, transactionId, requireNotNull(saved.id))
        return saved
    }

    private fun requireRecurringPayment(
        accountId: UUID,
        recurringPaymentId: UUID,
    ): RecurringPayment =
        recurringPaymentRepository.findById(recurringPaymentId, accountId)
            ?: throw NotFoundException(
                errorCode = CommonErrorCode.RESOURCE_NOT_FOUND,
                field = "id",
                parameters = mapOf("resource" to "RecurringPayment", "id" to recurringPaymentId),
            )
}

data class CreateRecurringPaymentCommand(
    val financialAccountId: UUID?,
    val name: String,
    val creditDebitType: io.github.salomax.neotool.banking.domain.transaction.CreditDebitType,
    val type: TransactionType?,
    val expectedAmount: BigDecimal?,
    val currencyCode: String,
    val categoryId: UUID?,
    val merchantId: UUID?,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int?,
    val intervalDays: Int?,
    val remindDaysBefore: Int,
    val firstDueDate: LocalDate,
    val notes: String?,
)

data class UpdateRecurringPaymentCommand(
    val financialAccountIdDefined: Boolean,
    val financialAccountId: UUID?,
    val name: String?,
    val creditDebitType: io.github.salomax.neotool.banking.domain.transaction.CreditDebitType?,
    val expectedAmountDefined: Boolean,
    val expectedAmount: BigDecimal?,
    val categoryIdDefined: Boolean,
    val categoryId: UUID?,
    val merchantIdDefined: Boolean,
    val merchantId: UUID?,
    val frequency: RecurringFrequency?,
    val dayOfMonthDefined: Boolean,
    val dayOfMonth: Int?,
    val intervalDaysDefined: Boolean,
    val intervalDays: Int?,
    val remindDaysBefore: Int?,
    val nextDueDate: LocalDate?,
    val notesDefined: Boolean,
    val notes: String?,
)

data class CreateRecurringPaymentFromTransactionCommand(
    val financialAccountIdOverrideDefined: Boolean,
    val financialAccountIdOverride: UUID?,
    val name: String?,
    val type: TransactionType?,
    val expectedAmount: BigDecimal?,
    val currencyCode: String?,
    val categoryIdDefined: Boolean,
    val categoryId: UUID?,
    val merchantIdDefined: Boolean,
    val merchantId: UUID?,
    val frequency: RecurringFrequency?,
    val dayOfMonth: Int?,
    val intervalDays: Int?,
    val remindDaysBefore: Int?,
    val firstDueDate: LocalDate?,
    val notes: String?,
)
