package io.github.salomax.neotool.banking.domain.transaction

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Domain model for banking.transactions (core ledger entry).
 *
 * Design rules:
 *  - [amount] is always positive; direction is [creditDebitType].
 *  - Balance is never stored; derive via SUM queries.
 *  - [BALANCE_ADJUSTMENT] type is neutral (not INCOME/EXPENSE/TRANSFER).
 *  - Transfers: two rows linked by [transferPeerTransactionId].
 *  - Installments: N rows per purchase, [installmentNumber]/[totalInstallments] tracks position.
 */
data class Transaction(
    val id: UUID? = null,

    // Ownership
    val financialAccountId: UUID,
    val accountId: UUID,
    val createdByUserId: UUID,
    /** User to whom this transaction belongs. Null when [belongsToName] is used. */
    val belongsToUserId: UUID? = createdByUserId,
    /** Name tag to whom this transaction belongs. Null when [belongsToUserId] is used. */
    val belongsToName: String? = null,

    // Classification
    val creditDebitType: CreditDebitType,
    val type: TransactionType,
    val status: TransactionStatus = TransactionStatus.POSTED,
    val source: TransactionSource = TransactionSource.MANUAL,

    // Amount
    val amount: BigDecimal,
    val currencyCode: String = "BRL",
    /** BRL equivalent for FX transactions (OFB: brazilianAmount). Null for BRL transactions. */
    val amountBrl: BigDecimal? = null,

    // Dates
    val date: LocalDate,
    val transactionDatetime: Instant? = null,

    // Description
    val description: String,
    /** Original institution text; immutable after OFB sync. */
    val descriptionRaw: String? = null,
    val notes: String? = null,
    /** Optional free-form tags assigned by users. */
    val tags: List<String> = emptyList(),

    // Category
    val categoryId: UUID? = null,

    // Counterparty (bank account transactions / PIX / TED)
    val counterpartyName: String? = null,
    val counterpartyDocument: String? = null,
    val counterpartyBankCode: String? = null,
    val counterpartyAccountNumber: String? = null,
    val paymentReason: String? = null,

    // Transfer between own accounts
    val transferPeerTransactionId: UUID? = null,

    // Credit card
    val billId: UUID? = null,
    val installmentNumber: Int? = null,
    val totalInstallments: Int? = null,
    val payeeMcc: Int? = null,
    /** Last 4 digits of the card (OFB: identificationNumber). */
    val cardIdentifier: String? = null,
    val billPostDate: LocalDate? = null,

    // Open Finance / Pluggy sync
    val providerTransactionId: String? = null,
    val providerAccountId: String? = null,

    // Import (file/OFX/CSV) dedup and audit
    val importFingerprint: String? = null,
    val importJobId: UUID? = null,

    // Soft delete + audit
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
