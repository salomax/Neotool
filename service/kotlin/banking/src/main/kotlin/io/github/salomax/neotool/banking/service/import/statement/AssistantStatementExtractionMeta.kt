package io.github.salomax.neotool.banking.service.import.statement

import com.fasterxml.jackson.annotation.JsonProperty

data class AssistantStatementPeriod(
    @param:JsonProperty("from") val from: String? = null,
    @param:JsonProperty("to") val to: String? = null,
)

data class AssistantStatementParserConfig(
    @param:JsonProperty("layout_type") val layoutType: String = "",
    @param:JsonProperty("date_format") val dateFormat: String = "",
    @param:JsonProperty("date_header_pattern") val dateHeaderPattern: String? = null,
    @param:JsonProperty("inline_transaction_date_pattern") val inlineTransactionDatePattern: String? = null,
    @param:JsonProperty("amount_pattern") val amountPattern: String = "",
    @param:JsonProperty("decimal_separator") val decimalSeparator: String = "comma",
    @param:JsonProperty("sign_rule") val signRule: String? = null,
    @param:JsonProperty("ignore_line_kinds") val ignoreLineKinds: List<String> = emptyList(),
    @param:JsonProperty("examples") val examples: List<String> = emptyList(),
    @param:JsonProperty("credit_patterns") val creditPatterns: List<String> = emptyList(),
    @param:JsonProperty("debit_patterns") val debitPatterns: List<String> = emptyList(),
    @param:JsonProperty("skip_line_patterns") val skipLinePatterns: List<String> = emptyList(),
    @param:JsonProperty("default_credit_debit_type") val defaultCreditDebitType: String? = null,
)

data class AssistantStatementExtractionMeta(
    @param:JsonProperty("bank_name") val bankName: String? = null,
    @param:JsonProperty("statement_period") val statementPeriod: AssistantStatementPeriod? = null,
    @param:JsonProperty("parser_config") val parserConfig: AssistantStatementParserConfig =
        AssistantStatementParserConfig(),
    @param:JsonProperty("warnings") val warnings: List<String> = emptyList(),
)
