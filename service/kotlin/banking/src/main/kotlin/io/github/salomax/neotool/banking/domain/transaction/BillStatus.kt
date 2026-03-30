package io.github.salomax.neotool.banking.domain.transaction

/**
 * Lifecycle status of a credit card bill (fatura).
 *
 * Transitions:
 *   OPEN    → closingDate >= today (bill still accepting charges)
 *   CLOSED  → closingDate < today and dueDate >= today (awaiting payment)
 *   OVERDUE → dueDate < today and not paid
 *   PAID    → set explicitly when full payment is registered; reverts to CLOSED/OVERDUE
 *             if the payment transaction is deleted (net total becomes positive again)
 */
enum class BillStatus { OPEN, CLOSED, PAID, OVERDUE }
