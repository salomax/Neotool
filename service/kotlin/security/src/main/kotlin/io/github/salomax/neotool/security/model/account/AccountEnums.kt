package io.github.salomax.neotool.security.model.account

/**
 * Account type (PERSONAL, FAMILY, BUSINESS).
 * Matches security.accounts.account_type CHECK constraint.
 */
enum class AccountType {
    PERSONAL,
    FAMILY,
    BUSINESS,
}

/**
 * Account lifecycle status.
 * Matches security.accounts.account_status CHECK constraint.
 */
enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    DELETED,
}

/**
 * Role of a user within an account.
 * Matches security.account_memberships.account_role CHECK constraint.
 */
enum class AccountRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER,
}

/**
 * Membership lifecycle status (invitation and active state).
 * Matches security.account_memberships.membership_status CHECK constraint.
 */
enum class MembershipStatus {
    PENDING,
    ACTIVE,
    REMOVED,
}
