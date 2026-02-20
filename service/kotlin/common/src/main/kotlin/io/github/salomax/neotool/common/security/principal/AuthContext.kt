package io.github.salomax.neotool.common.security.principal

import java.util.UUID

/**
 * Summary of a user's membership in an account (for token claims and auth context).
 */
data class AccountSummary(
    val accountId: UUID,
    val role: String,
)

/**
 * Normalized authentication context containing user identity and authorization data.
 *
 * This context is provider-agnostic and always contains non-null lists for roles and permissions,
 * even when empty, to avoid null handling downstream.
 *
 * Account fields are optional for backward compatibility: when absent, tokens and principals
 * behave as before (no account-scoped authorization).
 *
 * @param userId The user's unique identifier
 * @param email The user's email address
 * @param displayName The user's display name (may be null)
 * @param roles List of role names assigned to the user (direct and group-inherited). Always non-null, empty list if none.
 * @param permissions List of permission names granted to the user (via roles). Always non-null, empty list if none.
 * @param currentAccountId The user's current/active account (e.g. default). Null when no account context.
 * @param accounts List of account memberships (id + role). Null when not loaded; empty list when user has no ACTIVE memberships.
 * @param sessionVersion Revocation/version claim; when changed, stale tokens can be rejected. Null when not used.
 */
data class AuthContext(
    val userId: UUID,
    val email: String,
    val displayName: String?,
    val roles: List<String>,
    val permissions: List<String>,
    val currentAccountId: UUID? = null,
    val accounts: List<AccountSummary>? = null,
    val sessionVersion: Long? = null,
)
