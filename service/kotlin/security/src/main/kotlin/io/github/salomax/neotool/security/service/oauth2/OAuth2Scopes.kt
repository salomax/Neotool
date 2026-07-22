package io.github.salomax.neotool.security.service.oauth2

object OAuth2Scopes {
    const val FINANCE_TRANSACTIONS_READ = "finance:transactions:read"
    const val FINANCE_TRANSACTIONS_WRITE = "finance:transactions:write"
    const val FINANCE_CASHFLOW_READ = "finance:cashflow:read"
    const val FINANCE_ACCOUNTS_READ = "finance:accounts:read"
    const val FINANCE_BILLS_READ = "finance:bills:read"
    const val FINANCE_BILLS_WRITE = "finance:bills:write"
    const val FINANCE_CATEGORIES_READ = "finance:categories:read"
    const val FINANCE_CATEGORIES_WRITE = "finance:categories:write"
    const val FINANCE_RULES_READ = "finance:rules:read"
    const val FINANCE_RULES_WRITE = "finance:rules:write"

    val all: Set<String> =
        setOf(
            FINANCE_TRANSACTIONS_READ,
            FINANCE_TRANSACTIONS_WRITE,
            FINANCE_CASHFLOW_READ,
            FINANCE_ACCOUNTS_READ,
            FINANCE_BILLS_READ,
            FINANCE_BILLS_WRITE,
            FINANCE_CATEGORIES_READ,
            FINANCE_CATEGORIES_WRITE,
            FINANCE_RULES_READ,
            FINANCE_RULES_WRITE,
        )
}
