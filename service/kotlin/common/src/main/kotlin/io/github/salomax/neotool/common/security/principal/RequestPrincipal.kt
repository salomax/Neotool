package io.github.salomax.neotool.common.security.principal

import java.util.UUID

/**
 * Represents the authenticated principal for a request.
 * Supports both user and service principals with optional user context propagation.
 *
 * Account claims (currentAccountId, accounts, sessionVersion) are populated from JWT for user
 * access tokens when present; null for service tokens or legacy tokens without account context.
 *
 * @param principalType The type of principal (USER or SERVICE)
 * @param userId The user ID (nullable for service-only tokens)
 * @param serviceId The service ID (nullable, only for service principals)
 * @param token The raw JWT token string
 * @param tokenId Unique token identifier from the JWT `jti` claim.
 * @param clientId OAuth client id from the inbound access token when present.
 * For delegated SERVICE principals this remains null by design, because the delegated
 * token is intentionally not allowed to re-assert the original caller's client_id.
 * @param permissionsFromToken List of permissions extracted from the token claims
 * @param userPermissions Optional user permissions when user context is propagated in a service token
 * @param currentAccountId Current/active account from token (user access tokens only). Null when absent.
 * @param accounts Account memberships (id + role) from token. Null when absent.
 * @param sessionVersion Revocation/version claim from token. Null when absent.
 * @param delegatedBy Delegation context owner identifier (service tokens only).
 */
data class RequestPrincipal(
    val principalType: PrincipalType,
    val userId: UUID?,
    val serviceId: UUID?,
    val token: String,
    val tokenId: String,
    val clientId: String? = null,
    val permissionsFromToken: List<String>,
    val userPermissions: List<String>? = null,
    val currentAccountId: UUID? = null,
    val accounts: List<AccountSummary>? = null,
    val sessionVersion: Long? = null,
    val delegatedBy: String? = null,
) {
    /**
     * List of account IDs the principal is a member of (from token).
     * Empty list when accounts claim is absent; never null.
     */
    val accountIds: List<UUID>
        get() = accounts?.map { it.accountId } ?: emptyList()

    /**
     * Convenience constructor for user principals (backward compatibility).
     */
    constructor(
        userId: UUID,
        token: String,
        permissionsFromToken: List<String>,
        tokenId: String,
    ) : this(
        principalType = PrincipalType.USER,
        userId = userId,
        serviceId = null,
        token = token,
        tokenId = tokenId,
        clientId = null,
        permissionsFromToken = permissionsFromToken,
        userPermissions = null,
        currentAccountId = null,
        accounts = null,
        sessionVersion = null,
        delegatedBy = null,
    )

    /**
     * Returns the ID of the entity performing the action (the "actor").
     *
     * For USER principals: returns userId
     * For SERVICE principals: returns userId if user context is propagated, otherwise serviceId
     *
     * @return The actor ID, or null if neither userId nor serviceId is available (should not happen in practice)
     */
    val actorId: UUID?
        get() =
            when (principalType) {
                PrincipalType.USER -> userId
                PrincipalType.SERVICE -> userId ?: serviceId
            }

    /**
     * Returns true if this principal has an account context (current account and/or accounts list).
     */
    fun hasAccountContext(): Boolean = currentAccountId != null || !accounts.isNullOrEmpty()

    /**
     * Returns true if the principal is a member of the given account (current or in accounts list).
     * Use for account-scoped authorization checks without custom claim parsing.
     */
    fun isMemberOfAccount(accountId: UUID): Boolean = currentAccountId == accountId || accountIds.contains(accountId)

    /**
     * Returns true if the principal's session version matches the expected value.
     * Use for freshness/revocation checks: when the server bumps session version (e.g. on role change),
     * stale tokens can be rejected by comparing principal.sessionVersion to the stored expected value.
     * When either side is null, returns true (no version check).
     */
    fun isSessionVersionFresh(expectedSessionVersion: Long?): Boolean =
        expectedSessionVersion == null || sessionVersion == null || sessionVersion == expectedSessionVersion

    /**
     * Role string for the given account from token claims (e.g. `OWNER`), or null if unknown.
     */
    fun roleInAccount(accountId: UUID): String? = accounts?.firstOrNull { it.accountId == accountId }?.role

    fun isOwnerOfAccount(accountId: UUID): Boolean = roleInAccount(accountId) == "OWNER"
}
