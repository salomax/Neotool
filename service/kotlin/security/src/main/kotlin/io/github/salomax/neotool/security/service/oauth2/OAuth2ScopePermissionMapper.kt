package io.github.salomax.neotool.security.service.oauth2

import jakarta.inject.Singleton

interface OAuth2ScopePermissionMapper {
    fun mapScopes(scopes: Collection<String>): Set<String>
}

@Singleton
class DefaultOAuth2ScopePermissionMapper : OAuth2ScopePermissionMapper {
    private val scopePermissions =
        mapOf(
            "finance:cashflow:read" to setOf("banking:transaction:view"),
            "finance:transactions:read" to setOf("banking:transaction:view"),
            "finance:transactions:write" to
                setOf(
                    "banking:transaction:create",
                    "banking:transaction:update",
                    "banking:transaction:delete",
                ),
            "finance:accounts:read" to setOf("banking:account:view"),
            "finance:bills:read" to setOf("banking:transaction:view"),
            "finance:categories:read" to setOf("banking:category:view"),
            "finance:categories:write" to setOf("banking:category:manage"),
        )

    override fun mapScopes(scopes: Collection<String>): Set<String> =
        scopes.flatMap { scopePermissions[it].orEmpty() }.toSet()
}
