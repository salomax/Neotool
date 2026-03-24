package io.github.salomax.neotool.banking.client.transaction.security

import io.github.salomax.neotool.banking.domain.transaction.port.AccountMembershipValidationPort
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Fallback when service-to-service GraphQL client is not configured.
 */
@Singleton
@Requires(missingBeans = [SecurityAccountMembershipValidationPort::class])
open class UnavailableAccountMembershipValidationPort : AccountMembershipValidationPort {
    override fun isUserActiveMemberOfAccount(
        userId: UUID,
        accountId: UUID,
    ): Boolean {
        throw BusinessRuleException(
            errorCode = CommonErrorCode.SERVICE_UNAVAILABLE,
            field = "belongsToUserId",
            parameters = mapOf("dependency" to "security"),
        )
    }
}
