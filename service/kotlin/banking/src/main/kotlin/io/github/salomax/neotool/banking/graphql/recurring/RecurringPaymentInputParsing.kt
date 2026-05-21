package io.github.salomax.neotool.banking.graphql.recurring

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.RecurringFrequency
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.graphql.parseEnumValue
import io.github.salomax.neotool.banking.service.transaction.CreateRecurringPaymentCommand
import io.github.salomax.neotool.banking.service.transaction.CreateRecurringPaymentFromTransactionCommand
import io.github.salomax.neotool.banking.service.transaction.UpdateRecurringPaymentCommand
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal object RecurringPaymentInputParsing {
    fun parseCreateInput(input: Map<String, Any?>): CreateRecurringPaymentCommand =
        CreateRecurringPaymentCommand(
            financialAccountId = input.uuidOrNull("financialAccountId"),
            name = input.requiredString("name"),
            creditDebitType = input.requiredEnum("creditDebitType"),
            type = input.enumOrNull<TransactionType>("type"),
            expectedAmount = input.decimalOrNull("expectedAmount"),
            currencyCode = input.stringOrNull("currencyCode") ?: "BRL",
            categoryId = input.uuidOrNull("categoryId"),
            merchantId = input.uuidOrNull("merchantId"),
            frequency = input.requiredEnum("frequency"),
            dayOfMonth = input.intOrNull("dayOfMonth"),
            intervalDays = input.intOrNull("intervalDays"),
            remindDaysBefore = input.intOrNull("remindDaysBefore") ?: 3,
            firstDueDate = input.requiredDate("firstDueDate"),
            notes = input.stringOrNull("notes"),
        )

    fun parseUpdateInput(input: Map<String, Any?>): UpdateRecurringPaymentCommand =
        UpdateRecurringPaymentCommand(
            financialAccountIdDefined = input.containsKey("financialAccountId"),
            financialAccountId = input.uuidOrNull("financialAccountId"),
            name = input.stringOrNull("name"),
            creditDebitType = input.enumOrNull<CreditDebitType>("creditDebitType"),
            expectedAmountDefined = input.containsKey("expectedAmount"),
            expectedAmount = input.decimalOrNull("expectedAmount"),
            categoryIdDefined = input.containsKey("categoryId"),
            categoryId = input.uuidOrNull("categoryId"),
            merchantIdDefined = input.containsKey("merchantId"),
            merchantId = input.uuidOrNull("merchantId"),
            frequency = input.enumOrNull<RecurringFrequency>("frequency"),
            dayOfMonthDefined = input.containsKey("dayOfMonth"),
            dayOfMonth = input.intOrNull("dayOfMonth"),
            intervalDaysDefined = input.containsKey("intervalDays"),
            intervalDays = input.intOrNull("intervalDays"),
            remindDaysBefore = input.intOrNull("remindDaysBefore"),
            nextDueDate = input.dateOrNull("nextDueDate"),
            notesDefined = input.containsKey("notes"),
            notes = input.stringOrNull("notes"),
        )

    fun parseCreateFromTransactionInput(input: Map<String, Any?>): CreateRecurringPaymentFromTransactionCommand =
        CreateRecurringPaymentFromTransactionCommand(
            financialAccountIdOverrideDefined = input.containsKey("financialAccountId"),
            financialAccountIdOverride =
                if (input.containsKey("financialAccountId")) {
                    input.uuidOrNull("financialAccountId")
                } else {
                    null
                },
            name = input.stringOrNull("name"),
            type = input.enumOrNull<TransactionType>("type"),
            expectedAmount = input.decimalOrNull("expectedAmount"),
            currencyCode = input.stringOrNull("currencyCode"),
            categoryIdDefined = input.containsKey("categoryId"),
            categoryId = input.uuidOrNull("categoryId"),
            merchantIdDefined = input.containsKey("merchantId"),
            merchantId = input.uuidOrNull("merchantId"),
            frequency = input.enumOrNull<RecurringFrequency>("frequency"),
            dayOfMonth = input.intOrNull("dayOfMonth"),
            intervalDays = input.intOrNull("intervalDays"),
            remindDaysBefore = input.intOrNull("remindDaysBefore"),
            firstDueDate = input.dateOrNull("firstDueDate"),
            notes = input.stringOrNull("notes"),
        )

    private inline fun <reified T : Enum<T>> Map<String, Any?>.enumOrNull(key: String): T? =
        (this[key] as? String)?.let { parseEnumValue<T>(key, it) }

    private inline fun <reified T : Enum<T>> Map<String, Any?>.requiredEnum(key: String): T =
        parseEnumValue(key, requiredString(key))

    private fun Map<String, Any?>.requiredString(key: String): String =
        (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw invalidInput(key, "is required")

    private fun Map<String, Any?>.stringOrNull(key: String): String? = (this[key] as? String)?.trim()

    private fun Map<String, Any?>.uuidOrNull(key: String): UUID? {
        val raw = this[key] as? String ?: return null
        return runCatching { UUID.fromString(raw) }
            .getOrElse { throw invalidInput(key, "must be a valid UUID") }
    }

    private fun Map<String, Any?>.intOrNull(key: String): Int? =
        when (val raw = this[key]) {
            is Int -> raw
            is Long -> raw.toInt()
            is Double -> raw.toInt()
            is Float -> raw.toInt()
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }

    private fun Map<String, Any?>.decimalOrNull(key: String): BigDecimal? =
        when (val raw = this[key]) {
            null -> null
            is BigDecimal -> raw
            is Number -> BigDecimal(raw.toString())
            is String -> raw.toBigDecimalOrNull()
            else -> null
        }

    private fun Map<String, Any?>.dateOrNull(key: String): LocalDate? =
        when (val raw = this[key]) {
            null -> null
            is LocalDate -> raw
            is String ->
                runCatching { LocalDate.parse(raw) }
                    .getOrElse { throw invalidInput(key, "must be a valid date") }
            else -> null
        }

    private fun Map<String, Any?>.requiredDate(key: String): LocalDate =
        dateOrNull(key) ?: throw invalidInput(key, "is required")

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
