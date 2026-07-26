package io.github.salomax.neotool.banking.domain.pendingaction

enum class PendingActionType {
    INVESTMENT_MATURED,
    COUPON_PAYMENT_DUE,
    BANK_ACCOUNT_NEGATIVE_BALANCE,
    RECURRING_PAYMENT_DUE,
    AGENT_WRITE_CONFIRMATION,
}
