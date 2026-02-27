package io.github.salomax.neotool.banking.domain.rbac

/**
 * Typed constants for banking permissions.
 * Use these constants instead of string literals to avoid typos and ensure consistency.
 */
object BankingPermissions {
    /** Permission to view issuers (financial institutions) list. */
    const val BANKING_ISSUER_VIEW = "banking:issuer:view"
}
