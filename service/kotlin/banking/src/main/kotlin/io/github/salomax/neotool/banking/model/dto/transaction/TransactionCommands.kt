package io.github.salomax.neotool.banking.model.dto.transaction

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Command to create a new transaction.
 */
data class CreateTransactionCommand(
    val financialAccountId: UUID,
    val creditDebitType: CreditDebitType,
    val type: TransactionType,
    val amount: BigDecimal,
    val date: LocalDate,
    val description: String,
    val status: TransactionStatus = TransactionStatus.POSTED,
    val currencyCode: String = "BRL",
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val categoryId: UUID? = null,
    val merchantId: UUID? = null,
    val belongsToUserId: UUID? = null,
    val belongsToName: String? = null,
    val counterpartyName: String? = null,
    val counterpartyDocument: String? = null,
    val counterpartyBankCode: String? = null,
    val counterpartyAccountNumber: String? = null,
    val paymentReason: String? = null,
    val installmentNumber: Int? = null,
    val totalInstallments: Int? = null,
    val cardIdentifier: String? = null,
    val payeeMcc: Int? = null,
    /** When set (e.g. for payBill), used instead of resolving bill from date. */
    val billId: UUID? = null,
    /** Peer transaction ID for the other leg of a transfer (e.g. payBill bank leg). */
    val transferPeerTransactionId: UUID? = null,
    /** Import/sync source for the row. Defaults to MANUAL for user-entered transactions. */
    val transactionSource: TransactionSource = TransactionSource.MANUAL,
    /** Links this cash movement to banking.investment_transactions (dedup / traceability). */
    val investmentTransactionId: UUID? = null,
    /**
     * When true, skips rule-engine suggestion and uses [categoryId] / tags as provided.
     */
    val skipRuleEngine: Boolean = false,
    /** Optional provider id when [transactionSource] is not MANUAL. */
    val providerTransactionId: String? = null,
)

/**
 * Command to update an existing transaction.
 */
data class UpdateTransactionCommand(
    val creditDebitType: CreditDebitType? = null,
    val type: TransactionType? = null,
    val amount: BigDecimal? = null,
    val date: LocalDate? = null,
    val description: String? = null,
    val status: TransactionStatus? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
    val tagsProvided: Boolean = false,
    val categoryId: UUID? = null,
    /** True when the caller explicitly set categoryId (even to null = clear category). */
    val categoryIdProvided: Boolean = false,
    val merchantId: UUID? = null,
    /** True when the caller explicitly set merchantId (even to null = clear merchant). */
    val merchantIdProvided: Boolean = false,
    val belongsToUserId: UUID? = null,
    val belongsToName: String? = null,
    val belongsToProvided: Boolean = false,
    val counterpartyName: String? = null,
    val counterpartyDocument: String? = null,
    val counterpartyBankCode: String? = null,
    val counterpartyAccountNumber: String? = null,
    val paymentReason: String? = null,
    val cardIdentifier: String? = null,
    val payeeMcc: Int? = null,
    val transferPeerTransactionId: UUID? = null,
)
