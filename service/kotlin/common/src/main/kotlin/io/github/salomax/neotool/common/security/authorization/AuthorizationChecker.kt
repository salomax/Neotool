package io.github.salomax.neotool.common.security.authorization

import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal

/**
 * Common interface for authorization checking.
 * Implementations can range from simple token-based checks to complex database-backed
 * authorization with ABAC policies.
 *
 * This interface provides a unified contract for permission checking across different
 * modules and authorization strategies.
 */
interface AuthorizationChecker {
    /**
     * Require a permission for the given principal.
     * Throws AuthorizationDeniedException if the permission is not granted.
     *
     * @param principal The authenticated request principal
     * @param permission The permission/action to check (e.g., "assets:asset:view", "security:user:view")
     * @throws AuthorizationDeniedException if permission is not granted
     */
    fun require(
        principal: RequestPrincipal,
        permission: String,
    )
}
