package io.github.salomax.neotool.assistant.infra.kafka

import jakarta.inject.Singleton
import java.time.LocalDate

@Singleton
class StatementExtractionMetaValidator {
    fun validate(meta: StatementExtractionMeta): List<String> {
        val errors = mutableListOf<String>()
        val normalized = meta.normalized()

        normalized.statementPeriod?.from?.let { validateIsoDate(it, "statement_period.from", errors) }
        normalized.statementPeriod?.to?.let { validateIsoDate(it, "statement_period.to", errors) }

        val parser = normalized.parserConfig
        if (parser.layoutType !in VALID_LAYOUT_TYPES) {
            errors += "parser_config.layout_type must be one of $VALID_LAYOUT_TYPES"
        }
        if (parser.dateFormat !in VALID_DATE_FORMATS) {
            errors += "parser_config.date_format must be one of $VALID_DATE_FORMATS"
        }
        if (parser.decimalSeparator.lowercase() !in listOf("comma", "period")) {
            errors += "parser_config.decimal_separator must be 'comma' or 'period'"
        }
        if (
            parser.signRule.isNullOrBlank() &&
            parser.creditPatterns.isEmpty() &&
            parser.debitPatterns.isEmpty() &&
            parser.defaultCreditDebitType == null
        ) {
            errors += "parser_config.sign_rule must be provided unless legacy credit/debit patterns or default_credit_debit_type are present"
        }
        if (parser.layoutType == LAYOUT_DATE_GROUPED_MULTILINE && parser.dateHeaderPattern.isNullOrBlank()) {
            errors += "parser_config.date_header_pattern must be provided for DATE_GROUPED_MULTILINE"
        }
        if (parser.layoutType == LAYOUT_INLINE_ROWS && parser.inlineTransactionDatePattern.isNullOrBlank()) {
            errors += "parser_config.inline_transaction_date_pattern must be provided for INLINE_ROWS"
        }
        validateRegex(parser.amountPattern, "parser_config.amount_pattern", errors)
        parser.dateHeaderPattern?.let { validateRegex(it, "parser_config.date_header_pattern", errors) }
        parser.inlineTransactionDatePattern?.let { validateRegex(it, "parser_config.inline_transaction_date_pattern", errors) }
        parser.signRule?.let {
            if (it !in VALID_SIGN_RULES) {
                errors += "parser_config.sign_rule must be one of $VALID_SIGN_RULES when present"
            }
        }
        parser.ignoreLineKinds.forEachIndexed { index, kind ->
            if (kind !in VALID_IGNORE_LINE_KINDS) {
                errors += "parser_config.ignore_line_kinds[$index] must be one of $VALID_IGNORE_LINE_KINDS"
            }
        }
        parser.examples.forEachIndexed { index, example ->
            if (example.isBlank()) {
                errors += "parser_config.examples[$index] must not be blank"
            }
        }
        parser.creditPatterns.forEachIndexed { index, pattern ->
            validateRegex(pattern, "parser_config.credit_patterns[$index]", errors)
        }
        parser.debitPatterns.forEachIndexed { index, pattern ->
            validateRegex(pattern, "parser_config.debit_patterns[$index]", errors)
        }
        parser.skipLinePatterns.forEachIndexed { index, pattern ->
            validateRegex(pattern, "parser_config.skip_line_patterns[$index]", errors)
        }
        parser.defaultCreditDebitType?.let {
            if (it !in VALID_CREDIT_DEBIT_TYPES) {
                errors += "parser_config.default_credit_debit_type must be DEBIT or CREDIT when present"
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
        if (pattern.isBlank()) {
            errors += "$field must not be blank"
            return
        }
        runCatching { Regex(pattern) }.onFailure { e ->
            errors += "$field is not a valid regex: ${e.message}"
        }
    }

    private companion object {
        const val LAYOUT_DATE_GROUPED_MULTILINE = "DATE_GROUPED_MULTILINE"
        const val LAYOUT_INLINE_ROWS = "INLINE_ROWS"
        val VALID_LAYOUT_TYPES = listOf(LAYOUT_DATE_GROUPED_MULTILINE, LAYOUT_INLINE_ROWS)
        val VALID_DATE_FORMATS = listOf("DD_MMM_YYYY", "DD_MM_YYYY", "DD_MM")
        val VALID_SIGN_RULES = listOf("KEYWORD_BASED", "NEGATIVE_IS_DEBIT", "DEFAULT_TYPE")
        val VALID_IGNORE_LINE_KINDS = listOf("DAY_TOTAL", "RUNNING_BALANCE", "SECTION_HEADER", "HEADER", "FOOTER")
        val VALID_CREDIT_DEBIT_TYPES = setOf("DEBIT", "CREDIT")
    }
}
