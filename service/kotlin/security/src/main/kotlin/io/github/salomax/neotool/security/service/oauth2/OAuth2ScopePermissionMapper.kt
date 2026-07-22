package io.github.salomax.neotool.security.service.oauth2

import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton

interface OAuth2ScopePermissionMapper {
    fun allScopes(): Set<String>

    fun mapScopes(scopes: Collection<String>): Set<String>

    fun requiredPermissions(scope: String): Set<String>
}

@Singleton
class DefaultOAuth2ScopePermissionMapper : OAuth2ScopePermissionMapper {
    private val scopePermissions =
        mapOf(
            OAuth2Scopes.FINANCE_CASHFLOW_READ to setOf("banking:transaction:view"),
            OAuth2Scopes.FINANCE_TRANSACTIONS_READ to setOf("banking:transaction:view"),
            OAuth2Scopes.FINANCE_TRANSACTIONS_WRITE to
                setOf(
                    "banking:transaction:create",
                    "banking:transaction:update",
                    "banking:transaction:delete",
                ),
            OAuth2Scopes.FINANCE_ACCOUNTS_READ to setOf("banking:account:view"),
            OAuth2Scopes.FINANCE_BILLS_READ to setOf("banking:transaction:view"),
            OAuth2Scopes.FINANCE_BILLS_WRITE to
                setOf(
                    "banking:transaction:create",
                    "banking:transaction:update",
                    "banking:transaction:delete",
                ),
            OAuth2Scopes.FINANCE_CATEGORIES_READ to setOf("banking:category:view"),
            OAuth2Scopes.FINANCE_CATEGORIES_WRITE to setOf("banking:category:manage"),
            OAuth2Scopes.FINANCE_RULES_READ to setOf("banking:category:view"),
            OAuth2Scopes.FINANCE_RULES_WRITE to setOf("banking:category:manage"),
        )

    @PostConstruct
    fun validateMappings() {
        val missing = OAuth2Scopes.all - scopePermissions.keys
        val unknown = scopePermissions.keys - OAuth2Scopes.all
        val empty = scopePermissions.filterValues { it.isEmpty() }.keys
        if (missing.isNotEmpty() || unknown.isNotEmpty() || empty.isNotEmpty()) {
            throw IllegalStateException(
                "Invalid OAuth scope mapping: missing=$missing unknown=$unknown empty=$empty",
            )
        }
    }

    override fun allScopes(): Set<String> = OAuth2Scopes.all

    override fun mapScopes(scopes: Collection<String>): Set<String> =
        scopes.flatMap { scopePermissions[it].orEmpty() }.toSet()

    override fun requiredPermissions(scope: String): Set<String> = scopePermissions[scope].orEmpty()
}
