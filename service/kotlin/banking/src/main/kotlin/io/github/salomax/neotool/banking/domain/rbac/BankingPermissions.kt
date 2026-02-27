package io.github.salomax.neotool.banking.domain.rbac

/**
 * Typed constants for banking permissions.
 */
object BankingPermissions {
    /** View the financial institutions (issuers) reference list. */
    const val BANKING_ISSUER_VIEW = "banking:issuer:view"

    /** List financial accounts belonging to the user's account. */
    const val BANKING_ACCOUNT_VIEW = "banking:account:view"

    /** Create a new financial account or card. */
    const val BANKING_ACCOUNT_CREATE = "banking:account:create"
}
