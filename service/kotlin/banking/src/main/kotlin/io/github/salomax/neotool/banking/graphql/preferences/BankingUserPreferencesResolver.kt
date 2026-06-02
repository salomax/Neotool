package io.github.salomax.neotool.banking.graphql.preferences

import io.github.salomax.neotool.banking.domain.preferences.BankingUserPreferences
import io.github.salomax.neotool.banking.domain.preferences.RuleApplicationMode
import io.github.salomax.neotool.banking.graphql.parseEnumValue
import io.github.salomax.neotool.banking.graphql.preferences.dto.BankingUserPreferencesDTO
import io.github.salomax.neotool.banking.service.preferences.UserRuleApplicationModeService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton

@Singleton
class BankingUserPreferencesResolver(
    private val service: UserRuleApplicationModeService,
) {
    fun bankingUserPreferences(principal: RequestPrincipal): BankingUserPreferencesDTO {
        val userId = requireNotNull(principal.userId) { "User ID required" }
        return service.getPreferences(userId).toDTO()
    }

    fun updateBankingUserPreferences(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): BankingUserPreferencesDTO {
        val userId = requireNotNull(principal.userId) { "User ID required" }
        val ruleApplicationModeRaw =
            input["ruleApplicationMode"] as? String
                ?: throw ValidationException(CommonErrorCode.INVALID_INPUT, field = "ruleApplicationMode")
        val ruleApplicationMode = parseEnumValue<RuleApplicationMode>("ruleApplicationMode", ruleApplicationModeRaw)

        return service.update(userId, ruleApplicationMode).toDTO()
    }

    private fun BankingUserPreferences.toDTO(): BankingUserPreferencesDTO =
        BankingUserPreferencesDTO(ruleApplicationMode = ruleApplicationMode)
}
