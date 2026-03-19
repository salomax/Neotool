package io.github.salomax.neotool.assistant.infra.kafka

import com.fasterxml.jackson.annotation.JsonProperty

data class StatementPeriodMeta(
    @param:JsonProperty("from") val from: String?,
    @param:JsonProperty("to") val to: String?,
)

data class StatementParserConfig(
    @param:JsonProperty("layout_type") val layoutType: String,
    @param:JsonProperty("date_format") val dateFormat: String,
    @param:JsonProperty("date_header_pattern") val dateHeaderPattern: String?,
    @param:JsonProperty("inline_transaction_date_pattern") val inlineTransactionDatePattern: String?,
    @param:JsonProperty("amount_pattern") val amountPattern: String,
    @param:JsonProperty("decimal_separator") val decimalSeparator: String,
    @param:JsonProperty("credit_patterns") val creditPatterns: List<String>,
    @param:JsonProperty("debit_patterns") val debitPatterns: List<String>,
    @param:JsonProperty("skip_line_patterns") val skipLinePatterns: List<String>,
    @param:JsonProperty("default_credit_debit_type") val defaultCreditDebitType: String?,
)

data class StatementExtractionMeta(
    @param:JsonProperty("bank_name") val bankName: String?,
    @param:JsonProperty("statement_period") val statementPeriod: StatementPeriodMeta?,
    @param:JsonProperty("parser_config") val parserConfig: StatementParserConfig,
    @param:JsonProperty("warnings") val warnings: List<String>,
)

fun StatementExtractionMeta.normalized(): StatementExtractionMeta =
    copy(
        parserConfig =
            parserConfig.copy(
                layoutType = normalizeLayoutType(parserConfig.layoutType),
                dateFormat = normalizeDateFormat(parserConfig.dateFormat),
                decimalSeparator = normalizeDecimalSeparator(parserConfig.decimalSeparator),
                defaultCreditDebitType = parserConfig.defaultCreditDebitType?.trim()?.uppercase(),
            ),
    )

private fun normalizeLayoutType(value: String): String {
    val token = normalizeToken(value)
    return when {
        token == "DATE_GROUPED_MULTILINE" -> "DATE_GROUPED_MULTILINE"
        token == "DATE_GROUPED" -> "DATE_GROUPED_MULTILINE"
        token == "DATE_GROUPED_MULTI_LINE" -> "DATE_GROUPED_MULTILINE"
        token == "GROUPED_BY_DATE" -> "DATE_GROUPED_MULTILINE"
        token == "INLINE_ROWS" -> "INLINE_ROWS"
        token == "INLINE_ROW" -> "INLINE_ROWS"
        token == "INLINE" -> "INLINE_ROWS"
        token == "INLINE_TRANSACTIONS" -> "INLINE_ROWS"
        else -> token
    }
}

private fun normalizeDateFormat(value: String): String {
    val token = normalizeToken(value)
    return when {
        token == "DD_MM" -> "DD_MM"
        token == "DD_MM_YYYY" -> "DD_MM_YYYY"
        token == "DD_MMM_YYYY" -> "DD_MMM_YYYY"
        token.contains("MMM") && token.contains("YYYY") -> "DD_MMM_YYYY"
        token.contains("MM") && token.contains("YYYY") -> "DD_MM_YYYY"
        token.contains("MM") -> "DD_MM"
        else -> token
    }
}

private fun normalizeDecimalSeparator(value: String): String {
    val token = normalizeToken(value)
    return when (token) {
        "COMMA", "COMMA_DECIMAL", "DECIMAL_COMMA", "VIRGULA", "PT_BR", "BR", "" -> "comma"
        ",", "COMMA_SEPARATOR" -> "comma"
        "PERIOD", "DOT", "POINT", "DECIMAL_POINT", "DECIMAL_PERIOD", "PONTO", "EN_US", "US", "." -> "period"
        else -> value.trim().lowercase()
    }
}

private fun normalizeToken(value: String): String =
    value
        .trim()
        .uppercase()
        .replace(Regex("[\\s\\-/]+"), "_")
