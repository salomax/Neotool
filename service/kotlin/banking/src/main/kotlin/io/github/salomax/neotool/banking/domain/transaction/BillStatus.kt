package io.github.salomax.neotool.banking.domain.transaction

/**
 * Lifecycle status of a credit card bill (fatura).
 *
 * Transitions:
 *   OPEN    → closingDate >= today (bill still accepting charges)
 *   CLOSED  → closingDate < today and dueDate >= today (awaiting payment)
 *   OVERDUE → dueDate < today and not paid
 *   PAID    → terminal state; only reversed if a payment transaction is deleted
 */
enum class BillStatus { OPEN, CLOSED, PAID, OVERDUE }
