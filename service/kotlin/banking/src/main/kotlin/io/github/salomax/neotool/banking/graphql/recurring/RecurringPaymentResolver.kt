package io.github.salomax.neotool.banking.graphql.recurring

import io.github.salomax.neotool.banking.domain.transaction.RecurringPayment
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.transaction.RecurringPaymentCommandService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.UUID

@Singleton
class RecurringPaymentResolver(
    private val recurringPaymentCommandService: RecurringPaymentCommandService,
    private val transactionResolver: TransactionResolver,
    private val transactionCategoryRepository: TransactionCategoryRepository,
    private val merchantRepository: MerchantRepository,
) {
    fun recurringPayments(
        financialAccountId: String?,
        principal: RequestPrincipal,
    ): List<RecurringPaymentDTO> {
        val accountId = requireAccountId(principal)
        val parsedFinancialAccountId = financialAccountId?.takeIf { it.isNotBlank() }?.let {
            parseUuid(it, "financialAccountId")
        }
        return recurringPaymentCommandService
            .listRecurringPayments(accountId, parsedFinancialAccountId)
            .map { it.toDto(accountId) }
    }

    fun recurringPayment(
        id: String,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO? {
        val accountId = requireAccountId(principal)
        val recurringPaymentId = parseUuid(id, "id")
        return recurringPaymentCommandService.findRecurringPayment(accountId, recurringPaymentId)?.toDto(accountId)
    }

    fun suggestRecurringForTransaction(
        transactionId: String,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO? {
        val accountId = requireAccountId(principal)
        val parsedTransactionId = parseUuid(transactionId, "transactionId")
        return recurringPaymentCommandService
            .suggestRecurringForTransaction(accountId, parsedTransactionId)
            ?.toDto(accountId)
    }

    fun createRecurringPayment(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO {
        val accountId = requireAccountId(principal)
        val command = RecurringPaymentInputParsing.parseCreateInput(input)
        return recurringPaymentCommandService.createRecurringPayment(accountId, command).toDto(accountId)
    }

    fun updateRecurringPayment(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO {
        val accountId = requireAccountId(principal)
        val recurringPaymentId = parseUuid(id, "id")
        val command = RecurringPaymentInputParsing.parseUpdateInput(input)
        return recurringPaymentCommandService.updateRecurringPayment(accountId, recurringPaymentId, command).toDto(accountId)
    }

    fun pauseRecurringPayment(
        id: String,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO {
        val accountId = requireAccountId(principal)
        val recurringPaymentId = parseUuid(id, "id")
        return recurringPaymentCommandService.pauseRecurringPayment(accountId, recurringPaymentId).toDto(accountId)
    }

    fun resumeRecurringPayment(
        id: String,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO {
        val accountId = requireAccountId(principal)
        val recurringPaymentId = parseUuid(id, "id")
        return recurringPaymentCommandService.resumeRecurringPayment(accountId, recurringPaymentId).toDto(accountId)
    }

    fun deleteRecurringPayment(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireAccountId(principal)
        val recurringPaymentId = parseUuid(id, "id")
        return recurringPaymentCommandService.deleteRecurringPayment(accountId, recurringPaymentId)
    }

    fun linkTransactionToRecurring(
        transactionId: String,
        recurringPaymentId: String,
        principal: RequestPrincipal,
    ): TransactionDTO {
        val accountId = requireAccountId(principal)
        val parsedTransactionId = parseUuid(transactionId, "transactionId")
        val parsedRecurringPaymentId = parseUuid(recurringPaymentId, "recurringPaymentId")
        val linked =
            recurringPaymentCommandService.linkTransactionToRecurring(
                accountId = accountId,
                transactionId = parsedTransactionId,
                recurringPaymentId = parsedRecurringPaymentId,
            )
        return transactionResolver.toTransactionDTO(linked, accountId)
    }

    fun confirmRecurringPayment(
        id: String,
        paymentDate: LocalDate,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO {
        val accountId = requireAccountId(principal)
        val recurringPaymentId = parseUuid(id, "id")
        return recurringPaymentCommandService
            .confirmRecurringPayment(accountId, recurringPaymentId, paymentDate)
            .toDto(accountId)
    }

    fun createRecurringPaymentFromTransaction(
        transactionId: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): RecurringPaymentDTO {
        val accountId = requireAccountId(principal)
        val parsedTransactionId = parseUuid(transactionId, "transactionId")
        val command = RecurringPaymentInputParsing.parseCreateFromTransactionInput(input)
        return recurringPaymentCommandService
            .createRecurringPaymentFromTransaction(accountId, parsedTransactionId, command)
            .toDto(accountId)
    }

    private fun RecurringPayment.toDto(accountId: UUID): RecurringPaymentDTO {
        val category =
            categoryId?.let(transactionCategoryRepository::findById)
                ?.takeIf { it.accountId == null || it.accountId == accountId }
                ?.let {
                    TransactionCategoryDTO(
                        id = requireNotNull(it.id),
                        parentId = it.parentId,
                        name = it.name,
                        applicableTo = it.applicableTo,
                        icon = it.icon,
                        color = it.color,
                        isSystem = it.isSystem,
                        sortOrder = it.sortOrder,
                    )
                }
        val merchant = merchantId?.let(::loadMerchantDTO)
        return RecurringPaymentDTO(
            id = requireNotNull(id),
            financialAccountId = financialAccountId,
            name = name,
            creditDebitType = creditDebitType,
            type = type,
            expectedAmount = expectedAmount?.toDouble(),
            currencyCode = currencyCode,
            frequency = frequency,
            dayOfMonth = dayOfMonth,
            intervalDays = intervalDays,
            remindDaysBefore = remindDaysBefore,
            status = status,
            nextDueDate = nextDueDate,
            lastPaidDate = lastPaidDate,
            notes = notes,
            category = category,
            merchant = merchant,
        )
    }

    private fun loadMerchantDTO(merchantId: UUID): MerchantDTO? {
        val merchant = merchantRepository.findById(merchantId) ?: return null
        val logoAssetId = merchant.domain?.let { merchantRepository.findLogoAssetIdByDomain(it) }
        return MerchantDTO(
            id = requireNotNull(merchant.id),
            name = merchant.name,
            domain = merchant.domain,
            sector = merchant.sector,
            isSystem = merchant.isSystem,
            isOwned = merchant.accountId != null,
            logoAsset = logoAssetId?.let { mapOf("__typename" to "Asset", "id" to it.toString()) },
        )
    }

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }

    private fun parseUuid(
        value: String,
        field: String,
    ): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw invalidInput(field, "must be a valid UUID") }

    private fun invalidInput(
        field: String,
        reason: String,
    ): ValidationException =
        ValidationException(
            errorCode = CommonErrorCode.INVALID_INPUT,
            field = field,
            parameters = mapOf("reason" to reason),
        )
}
