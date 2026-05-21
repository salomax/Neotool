package io.github.salomax.neotool.banking.domain.pendingaction

enum class AccountPendingActionStatus {
    PENDING,
    COMPLETED, // user confirmed
    DISMISSED, // user dismissed
    CANCELLED, // producer revoked
}
