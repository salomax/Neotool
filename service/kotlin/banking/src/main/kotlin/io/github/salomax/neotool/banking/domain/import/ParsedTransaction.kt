package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * A row extracted by any import parser (OFX, CSV, etc.).
 * No JPA or Micronaut dependency; used for import pipeline only.
 */
data class ParsedTransaction(
    val providerTransactionId: String? = null,
    val creditDebitType: CreditDebitType,
    val amount: BigDecimal,
    val date: LocalDate,
    val transactionDatetime: Instant? = null,
    val description: String,
    val descriptionRaw: String? = null,
    val counterpartyName: String? = null,
    val counterpartyDocument: String? = null,
    val currencyCode: String = "BRL",
    /** Total installments (parcelamento); e.g. from Nubank CSV "charges" column. */
    val totalInstallments: Int? = null,
)
