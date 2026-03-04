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

    /** View transactions for accounts belonging to the user's account. */
    const val BANKING_TRANSACTION_VIEW = "banking:transaction:view"

    /** Create a new transaction (manual entry). */
    const val BANKING_TRANSACTION_CREATE = "banking:transaction:create"

    /** Update an existing manually-entered transaction. */
    const val BANKING_TRANSACTION_UPDATE = "banking:transaction:update"

    /** Soft-delete a manually-entered transaction. */
    const val BANKING_TRANSACTION_DELETE = "banking:transaction:delete"

    /** View transaction categories (system + user-defined). */
    const val BANKING_CATEGORY_VIEW = "banking:category:view"

    /** Create and manage user-defined transaction categories. */
    const val BANKING_CATEGORY_MANAGE = "banking:category:manage"
}
