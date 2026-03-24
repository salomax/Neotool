package io.github.salomax.neotool.banking.client.transaction.security

import java.util.UUID

/**
 * Port used by banking to validate whether a user belongs to an account
 * according to the Security service source of truth.
 */
interface AccountMembershipValidationPort {
    fun isUserActiveMemberOfAccount(
        userId: UUID,
        accountId: UUID,
    ): Boolean
}
