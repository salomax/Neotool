package io.github.salomax.neotool.banking.domain.billimport

import com.fasterxml.jackson.annotation.JsonProperty

data class AssistantBillTransactionPattern(
    /** "DD_MMM" for "08 FEV" / "05 Nov"; "DD_MM" for "08/02"; "DD_MM_YYYY" for "08/02/2026" */
    @param:JsonProperty("date_format") val dateFormat: String = "",
    /** Regex with one capturing group to extract the 4-digit card suffix inline per transaction row.
     *  Null when each card has its own dedicated section (most common case). */
    @param:JsonProperty("last_four_pattern") val lastFourPattern: String? = null,
    /** Currency symbols present in amounts, e.g. ["R$"] or ["R$", "US$"] */
    @param:JsonProperty("currency_symbols") val currencySymbols: List<String> = emptyList(),
    /** "comma" for pt-BR format (R$ 49,40); "period" for US format ($49.40) */
    @param:JsonProperty("decimal_separator") val decimalSeparator: String = "comma",
    /** Regex with two capturing groups: (1) current installment, (2) total. Null if no installments. */
    @param:JsonProperty("installment_pattern") val installmentPattern: String? = null,
)

data class AssistantBillCardBlock(
    /** 4-digit card suffix from section header. Null only when last_four_pattern is set in TransactionPattern. */
    @param:JsonProperty("last_four") val lastFour: String? = null,
    @param:JsonProperty("declared_total") val declaredTotal: Double? = null,
    /** Literal substring marking the START of this card's transaction section. */
    @param:JsonProperty("anchor_start") val anchorStart: String = "",
)

data class AssistantBillExtractionMeta(
    @param:JsonProperty("bank_name") val bankName: String? = null,
    @param:JsonProperty("closing_date") val closingDate: String = "",
    @param:JsonProperty("due_date") val dueDate: String = "",
    @param:JsonProperty("declared_total") val declaredTotal: Double? = null,
    /** Empty when card IDs appear inline per row and the extractor must parse the whole document. */
    @param:JsonProperty("card_blocks") val cardBlocks: List<AssistantBillCardBlock> = emptyList(),
    @param:JsonProperty("transaction_pattern") val transactionPattern: AssistantBillTransactionPattern =
        AssistantBillTransactionPattern(),
)
