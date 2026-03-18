package io.github.salomax.neotool.assistant.infra.kafka

import com.fasterxml.jackson.annotation.JsonProperty

data class TransactionPattern(
    /** "DD_MMM" for "08 FEV" / "05 Nov"; "DD_MM" for "08/02"; "DD_MM_YYYY" for "08/02/2026" */
    @JsonProperty("date_format") val dateFormat: String,
    /** Regex with one capturing group to extract the 4-digit card suffix inline per transaction row.
     *  Null when each card has its own dedicated section (most common case). */
    @JsonProperty("last_four_pattern") val lastFourPattern: String?,
    /** Currency symbols present in amounts, e.g. ["R$"] or ["R$", "US$"] */
    @JsonProperty("currency_symbols") val currencySymbols: List<String>,
    /** "comma" for pt-BR format (R$ 49,40); "period" for US format ($49.40) */
    @JsonProperty("decimal_separator") val decimalSeparator: String,
    /** Regex with two capturing groups: (1) current installment, (2) total. Null if no installments. */
    @JsonProperty("installment_pattern") val installmentPattern: String?,
)

data class CardBlock(
    /** 4-digit card suffix from section header. Null only when last_four_pattern is set in TransactionPattern. */
    @JsonProperty("last_four") val lastFour: String?,
    @JsonProperty("declared_total") val declaredTotal: Double?,
    /** Literal substring marking the START of this card's transaction section. */
    @JsonProperty("anchor_start") val anchorStart: String,
)

data class BillExtractionMeta(
    @JsonProperty("bank_name") val bankName: String?,
    @JsonProperty("closing_date") val closingDate: String,
    @JsonProperty("due_date") val dueDate: String,
    @JsonProperty("declared_total") val declaredTotal: Double?,
    /** Empty when card IDs appear inline per row and the extractor must parse the whole document. */
    @JsonProperty("card_blocks") val cardBlocks: List<CardBlock>,
    @JsonProperty("transaction_pattern") val transactionPattern: TransactionPattern,
)
