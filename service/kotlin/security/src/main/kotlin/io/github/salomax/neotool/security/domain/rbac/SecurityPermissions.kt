package io.github.salomax.neotool.security.domain.rbac

/**
 * Typed constants for security permissions.
 * Use these constants instead of string literals to avoid typos and ensure consistency.
 */
object SecurityPermissions {
    // User management permissions
    const val SECURITY_USER_VIEW = "security:user:view"
    const val SECURITY_USER_SAVE = "security:user:save"
    const val SECURITY_USER_DELETE = "security:user:delete"

    // Group management permissions
    const val SECURITY_GROUP_VIEW = "security:group:view"
    const val SECURITY_GROUP_SAVE = "security:group:save"
    const val SECURITY_GROUP_DELETE = "security:group:delete"

    // Role management permissions
    const val SECURITY_ROLE_VIEW = "security:role:view"
    const val SECURITY_ROLE_SAVE = "security:role:save"
    const val SECURITY_ROLE_DELETE = "security:role:delete"
}
