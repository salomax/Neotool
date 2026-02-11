package io.github.salomax.neotool.security.error

import io.github.salomax.neotool.common.error.ErrorCode

/**
 * Security domain error codes.
 * These codes are sent to the frontend for i18n translation.
 */
enum class SecurityErrorCode(
    override val code: String,
    override val defaultMessage: String,
    override val httpStatus: Int = 400,
) : ErrorCode {
    // Authentication errors
    AUTHENTICATION_REQUIRED("AUTH_AUTHENTICATION_REQUIRED", "Authentication required", 401),
    INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid email or password", 401),
    TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Authentication token has expired", 401),
    TOKEN_INVALID("AUTH_TOKEN_INVALID", "Invalid authentication token", 401),
    SESSION_EXPIRED("AUTH_SESSION_EXPIRED", "Session has expired", 401),

    // Authorization errors
    AUTHORIZATION_DENIED("AUTH_AUTHORIZATION_DENIED", "Access denied", 403),
    INSUFFICIENT_PERMISSIONS("AUTH_INSUFFICIENT_PERMISSIONS", "Insufficient permissions", 403),
    RESOURCE_ACCESS_DENIED("AUTH_RESOURCE_ACCESS_DENIED", "Access to this resource is denied", 403),

    // Account validation errors
    ACCOUNT_NAME_REQUIRED("ACCOUNT_NAME_REQUIRED", "Account name is required and cannot be blank", 400),
    ACCOUNT_NAME_TOO_LONG("ACCOUNT_NAME_TOO_LONG", "Account name exceeds maximum length", 400),
    ACCOUNT_TYPE_INVALID("ACCOUNT_TYPE_INVALID", "Invalid account type", 400),
    ACCOUNT_TYPE_MUST_BE_FAMILY_OR_BUSINESS(
        "ACCOUNT_TYPE_MUST_BE_FAMILY_OR_BUSINESS",
        "Account type must be FAMILY or BUSINESS (PERSONAL is auto-created on signup)",
        400,
    ),

    // Account state errors
    ACCOUNT_NOT_FOUND("ACCOUNT_NOT_FOUND", "Account not found", 404),
    ACCOUNT_ALREADY_EXISTS("ACCOUNT_ALREADY_EXISTS", "Account already exists", 409),
    ACCOUNT_INACTIVE("ACCOUNT_INACTIVE", "Account is inactive", 403),
    ACCOUNT_SUSPENDED("ACCOUNT_SUSPENDED", "Account has been suspended", 403),
    ACCOUNT_DELETION_NOT_ALLOWED("ACCOUNT_DELETION_NOT_ALLOWED", "Account cannot be deleted", 409),
    MUST_HAVE_ONE_ACTIVE_ACCOUNT("MUST_HAVE_ONE_ACTIVE_ACCOUNT", "User must have at least one active account", 409),

    // User validation errors
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", 404),
    USER_EMAIL_REQUIRED("USER_EMAIL_REQUIRED", "Email is required", 400),
    USER_EMAIL_INVALID("USER_EMAIL_INVALID", "Invalid email format", 400),
    USER_EMAIL_ALREADY_EXISTS("USER_EMAIL_ALREADY_EXISTS", "Email already exists", 409),
    USER_PASSWORD_REQUIRED("USER_PASSWORD_REQUIRED", "Password is required", 400),
    USER_PASSWORD_TOO_SHORT("USER_PASSWORD_TOO_SHORT", "Password is too short", 400),
    USER_PASSWORD_TOO_WEAK("USER_PASSWORD_TOO_WEAK", "Password is too weak", 400),

    // Role and permission errors
    ROLE_NOT_FOUND("ROLE_NOT_FOUND", "Role not found", 404),
    ROLE_NAME_REQUIRED("ROLE_NAME_REQUIRED", "Role name is required", 400),
    PERMISSION_NOT_FOUND("PERMISSION_NOT_FOUND", "Permission not found", 404),
    GROUP_NOT_FOUND("GROUP_NOT_FOUND", "Group not found", 404),

    // Ownership errors
    NOT_ACCOUNT_OWNER("NOT_ACCOUNT_OWNER", "Only account owner can perform this action", 403),
    OWNERSHIP_REQUIRED("OWNERSHIP_REQUIRED", "Resource ownership required", 403),

    // Membership policy (FR-3, FR-6) – used by MembershipPolicyEngine
    NOT_ACCOUNT_MEMBER("NOT_ACCOUNT_MEMBER", "User has no active membership in this account", 403),
    ACCOUNT_ROLE_INSUFFICIENT("ACCOUNT_ROLE_INSUFFICIENT", "Account role does not allow this operation", 403),
    SELF_OPERATION_FORBIDDEN("SELF_OPERATION_FORBIDDEN", "Operation on self is not allowed", 403),
    TARGET_ROLE_TOO_HIGH("TARGET_ROLE_TOO_HIGH", "Target member's role cannot be changed or removed by caller", 403),
    TARGET_NOT_ELIGIBLE("TARGET_NOT_ELIGIBLE", "Target does not meet criteria for this operation", 403),
    SOLE_OWNER_CANNOT_LEAVE("SOLE_OWNER_CANNOT_LEAVE", "Owner cannot leave without transferring ownership first", 403),
    NOT_PENDING_INVITATION("NOT_PENDING_INVITATION", "Invitation is not in pending status", 400),

    // Invitation (FR-4)
    ALREADY_MEMBER("ALREADY_MEMBER", "User is already a member of this account", 409),
    MEMBER_LIMIT_REACHED("MEMBER_LIMIT_REACHED", "Account has reached maximum members for its type", 409),
    INVITATION_EXPIRED("INVITATION_EXPIRED", "Invitation has expired", 400),
}
