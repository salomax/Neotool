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
    PIX, TED, DOC, BOLETO, TRANSFER,
    // Income types
    SALARY, DEPOSIT, CASHBACK, INVESTMENT_RETURN, OTHER_INCOME,
    // Expense types
    CARD, WITHDRAWAL, FEE, INTEREST, LOAN_PAYMENT, TAX, OTHER_EXPENSE,
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
 */
enum class TransactionSource { MANUAL, OPEN_FINANCE, PLUGGY }

/**
 * Category applicability.
 */
enum class CategoryApplicableTo { INCOME, EXPENSE, TRANSFER, ANY }
