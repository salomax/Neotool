package io.github.salomax.neotool.assistant.infra.kafka

import com.fasterxml.jackson.databind.JsonNode
import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Singleton
class BankingReaderOutputValidator {
    fun validate(
        documentType: String,
        output: JsonNode,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (!output.isObject) {
            errors += "llm_output must be a JSON object"
            return errors
        }

        validateNullableString(output.path("bank_name"), "bank_name", errors)
        validateStatementPeriod(output.path("statement_period"), errors)
        validateCards(output.path("cards"), errors)
        validateConfidence(output.path("extraction_confidence"), "extraction_confidence", errors)
        validateBoolean(output.path("needs_review"), "needs_review", errors)
        validateStringArray(output.path("warnings"), "warnings", errors)

        return errors
    }

    private fun validateStatementPeriod(
        node: JsonNode,
        errors: MutableList<String>,
    ) {
        if (!node.isObject) {
            errors += "statement_period must be an object"
            return
        }
        validateDate(node.path("from"), "statement_period.from", errors, nullable = true)
        validateDate(node.path("to"), "statement_period.to", errors, nullable = true)
    }

    private fun validateCards(
        node: JsonNode,
        errors: MutableList<String>,
    ) {
        if (!node.isArray) {
            errors += "cards must be an array"
            return
        }
        node.forEachIndexed { index, card ->
            val prefix = "cards[$index]"
            if (!card.isObject) {
                errors += "$prefix must be an object"
                return@forEachIndexed
            }

            val digits = card.path("last_four_digits")
            if (!digits.isTextual || !FOUR_DIGITS_REGEX.matches(digits.asText())) {
                errors += "$prefix.last_four_digits must match ^[0-9]{4}$"
            }
            validateDate(card.path("closing_date"), "$prefix.closing_date", errors)
            validateDate(card.path("due_date"), "$prefix.due_date", errors)
            validateNumber(card.path("declared_total"), "$prefix.declared_total", errors)
            validateNumber(card.path("computed_total"), "$prefix.computed_total", errors)
            validateBoolean(card.path("total_matches_declared"), "$prefix.total_matches_declared", errors)
            validateConfidence(card.path("confidence"), "$prefix.confidence", errors)
            validateTransactions(card.path("transactions"), "$prefix.transactions", errors)
        }
    }

    private fun validateTransactions(
        node: JsonNode,
        prefix: String,
        errors: MutableList<String>,
    ) {
        if (!node.isArray) {
            errors += "$prefix must be an array"
            return
        }
        node.forEachIndexed { index, transaction ->
            val txPrefix = "$prefix[$index]"
            if (!transaction.isObject) {
                errors += "$txPrefix must be an object"
                return@forEachIndexed
            }

            validateDate(transaction.path("date"), "$txPrefix.date", errors)
            validateNonBlankString(transaction.path("description"), "$txPrefix.description", errors)
            validateNumber(transaction.path("amount"), "$txPrefix.amount", errors)
            validateConfidence(transaction.path("confidence"), "$txPrefix.confidence", errors)

            val creditDebit = transaction.path("credit_debit_type")
            if (!creditDebit.isTextual || !VALID_CREDIT_DEBIT_TYPES.contains(creditDebit.asText())) {
                errors += "$txPrefix.credit_debit_type must be DEBIT or CREDIT"
            }

            validateInstallment(transaction.path("installment"), "$txPrefix.installment", errors)
            validateOptionalBoolean(transaction.path("is_bill_payment"), "$txPrefix.is_bill_payment", errors)
        }
    }

    private fun validateOptionalBoolean(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (node.isMissingNode || node.isNull) return
        if (!node.isBoolean) {
            errors += "$path must be a boolean or null"
        }
    }

    private fun validateInstallment(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (node.isMissingNode || node.isNull) {
            return
        }
        if (!node.isObject) {
            errors += "$path must be null or an object"
            return
        }

        val current = node.path("current")
        val total = node.path("total")
        if (!current.isInt || current.asInt() < 1) {
            errors += "$path.current must be integer >= 1"
        }
        if (!total.isInt || total.asInt() < 1) {
            errors += "$path.total must be integer >= 1"
        }
    }

    private fun validateStringArray(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (!node.isArray) {
            errors += "$path must be an array"
            return
        }
        node.forEachIndexed { index, element ->
            if (!element.isTextual) {
                errors += "$path[$index] must be a string"
            }
        }
    }

    private fun validateNullableString(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (node.isNull) {
            return
        }
        if (!node.isTextual) {
            errors += "$path must be a string or null"
        }
    }

    private fun validateNonBlankString(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (!node.isTextual || node.asText().isBlank()) {
            errors += "$path must be a non-empty string"
        }
    }

    private fun validateNumber(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (!node.isNumber) {
            errors += "$path must be a number"
        }
    }

    private fun validateBoolean(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (!node.isBoolean) {
            errors += "$path must be a boolean"
        }
    }

    private fun validateDate(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
        nullable: Boolean = false,
    ) {
        if (node.isMissingNode || node.isNull) {
            if (!nullable) {
                errors += "$path must be an ISO date string"
            }
            return
        }
        if (!node.isTextual) {
            errors += "$path must be an ISO date string"
            return
        }
        val value = node.asText().trim()
        if (value.isEmpty()) {
            errors += "$path must be an ISO date string"
            return
        }
        try {
            parseDate(value)
        } catch (_: IllegalArgumentException) {
            errors += "$path must be a valid date (YYYY-MM-DD or DD/MM/YYYY)"
        }
    }

    private fun parseDate(value: String): LocalDate =
        runCatching { LocalDate.parse(value) }
            .recoverCatching { LocalDate.parse(value, BR_DATE_FORMATTER) }
            .getOrElse { throw IllegalArgumentException("Invalid date: $value", it) }

    private fun validateConfidence(
        node: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        if (!node.isNumber) {
            errors += "$path must be a number between 0 and 1"
            return
        }
        val value = node.asDouble()
        if (value < 0.0 || value > 1.0) {
            errors += "$path must be between 0 and 1"
        }
    }

    private companion object {
        val VALID_CREDIT_DEBIT_TYPES = setOf("DEBIT", "CREDIT")
        val FOUR_DIGITS_REGEX = Regex("^[0-9]{4}$")
        val BR_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
