package io.github.salomax.neotool.banking.service.billimport

import io.github.salomax.neotool.banking.domain.billimport.*
import jakarta.inject.Singleton
import java.time.LocalDate

@Singleton
class AssistantBillMetaValidator {
    fun validate(meta: AssistantBillExtractionMeta): List<String> {
        val errors = mutableListOf<String>()

        validateIsoDate(meta.closingDate, "closing_date", errors)
        validateIsoDate(meta.dueDate, "due_date", errors)

        val pattern = meta.transactionPattern
        if (pattern.currencySymbols.isEmpty()) {
            errors += "transaction_pattern.currency_symbols must not be empty"
        }
        if (pattern.decimalSeparator.lowercase() !in listOf("comma", "period")) {
            errors += "transaction_pattern.decimal_separator must be 'comma' or 'period', got: '${pattern.decimalSeparator}'"
        }
        pattern.lastFourPattern?.let { validateRegex(it, "transaction_pattern.last_four_pattern", errors) }
        pattern.installmentPattern?.let { validateRegex(it, "transaction_pattern.installment_pattern", errors) }

        if (meta.cardBlocks.isEmpty() && pattern.lastFourPattern == null) {
            errors += "card_blocks must not be empty when transaction_pattern.last_four_pattern is not set"
        }
        meta.cardBlocks.forEachIndexed { index, block ->
            val prefix = "card_blocks[$index]"
            if (pattern.lastFourPattern == null && block.lastFour.isNullOrBlank()) {
                errors += "$prefix.last_four must not be blank when transaction_pattern.last_four_pattern is not set"
            }
            if (block.anchorStart.isBlank()) {
                errors += "$prefix.anchor_start must not be blank"
            }
        }

        return errors
    }

    private fun validateIsoDate(value: String, field: String, errors: MutableList<String>) {
        if (value.isBlank()) {
            errors += "$field must not be blank"
            return
        }
        runCatching { LocalDate.parse(value) }.onFailure {
            errors += "$field must be a valid ISO date (YYYY-MM-DD), got: \"$value\""
        }
    }

    private fun validateRegex(pattern: String, field: String, errors: MutableList<String>) {
        runCatching { Regex(pattern) }.onFailure { e ->
            errors += "$field is not a valid regex: ${e.message}"
        }
    }
}
