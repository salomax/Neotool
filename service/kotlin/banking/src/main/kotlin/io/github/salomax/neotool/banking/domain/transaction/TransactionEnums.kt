package io.github.salomax.neotool.banking.domain.transaction

/**
 * Direction of money flow. Amount is always positive; direction is carried here.
 */
enum class CreditDebitType { CREDIT, DEBIT }

/**
 * Transaction type (OFB-aligned).
 * BALANCE_ADJUSTMENT is neutral — used for opening balance and reconciliation.
 */
enum class TransactionType {
    // Payment types (OFB-aligned)
    PIX,
    TED,
    DOC,
    BOLETO,
    TRANSFER,
    BILL_PAYMENT,

    // Income types
    SALARY,
    DEPOSIT,
    CASHBACK,
    INVESTMENT_RETURN,
    OTHER_INCOME,

    // Expense types
    CARD,
    WITHDRAWAL,
    FEE,
    INTEREST,
    LOAN_PAYMENT,
    TAX,
    OTHER_EXPENSE,

    // Neutral
    BALANCE_ADJUSTMENT,
}

/**
 * Lifecycle status of a transaction.
 * FUTURE = manually scheduled; excluded from current balance but included in projectedBalance.
 */
enum class TransactionStatus { POSTED, PENDING, FUTURE }

/**
 * How the transaction entered the system.
 * Tracks the import channel/format — not the specific provider or bank.
 * Provider (e.g. Pluggy, Belvo) and bank are implementation details captured elsewhere.
 */
enum class TransactionSource {
    MANUAL, // Typed by the user
    OPEN_FINANCE, // Via Open Finance Brasil API (any aggregator: Pluggy, Belvo, etc.)
    OFX, // File upload: OFX format
    CSV, // File upload: CSV format (bank-specific parser = ImportSource concern)
    PDF, // File upload: PDF format (any document type: bill, statement, etc.)
    NFC_E, // NFC-e receipt scan
    RECEIPT_OCR, // Receipt photo OCR
}

/**
 * Category applicability.
 */
enum class CategoryApplicableTo { INCOME, EXPENSE, TRANSFER, ANY }

/**
 * How the transaction's category was assigned.
 * MANUAL = user-selected; RULE = auto-categorization rule; IMPORTED = from import; AI = future.
 */
enum class CategorySource { MANUAL, RULE, IMPORTED, AI }

/** Pattern type for category rules (description matching). */
enum class PatternType { CONTAINS, STARTS_WITH, EXACT, REGEX }
