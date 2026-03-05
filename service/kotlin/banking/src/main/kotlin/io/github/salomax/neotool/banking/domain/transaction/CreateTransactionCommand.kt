package io.github.salomax.neotool.banking.domain.transaction

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Command to create a new [Transaction].
 *
 * Business rules enforced at service layer:
 *  - [amount] must be > 0.
 *  - For BALANCE_ADJUSTMENT: creditDebitType must be CREDIT.
 *  - For installments: both [installmentNumber] and [totalInstallments] must be provided together,
 *    and installmentNumber must be <= totalInstallments.
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
    val belongsToUserId: UUID? = null,
    val belongsToName: String? = null,

    // Counterparty
    val counterpartyName: String? = null,
    val counterpartyDocument: String? = null,
    val counterpartyBankCode: String? = null,
    val counterpartyAccountNumber: String? = null,
    val paymentReason: String? = null,

    // Installments (parcelamento): per-installment amount, tracked by position
    val installmentNumber: Int? = null,
    val totalInstallments: Int? = null,

    // Card
    val cardIdentifier: String? = null,
    val payeeMcc: Int? = null,

    /** When set (e.g. for payBill), used instead of resolving bill from date. */
    val billId: UUID? = null,
    /** Peer transaction ID for the other leg of a transfer (e.g. payBill bank leg). */
    val transferPeerTransactionId: UUID? = null,
)

/**
 * Command to update an existing [Transaction].
 * Only user-editable fields are included (OFB sync fields and source are immutable).
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
