package io.github.salomax.neotool.banking.graphql.preferences

import io.github.salomax.neotool.banking.domain.preferences.BankingUserPreferences
import io.github.salomax.neotool.banking.domain.preferences.RuleCreationMode
import io.github.salomax.neotool.banking.graphql.parseEnumValue
import io.github.salomax.neotool.banking.graphql.preferences.dto.BankingUserPreferencesDTO
import io.github.salomax.neotool.banking.service.preferences.UserRuleCreationModeService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton

@Singleton
class BankingUserPreferencesResolver(
    private val service: UserRuleCreationModeService,
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
        val ruleCreationModeRaw =
            input["ruleCreationMode"] as? String
                ?: throw ValidationException(CommonErrorCode.INVALID_INPUT, field = "ruleCreationMode")
        val ruleCreationMode = parseEnumValue<RuleCreationMode>("ruleCreationMode", ruleCreationModeRaw)

        return service.update(userId, ruleCreationMode).toDTO()
    }

    private fun BankingUserPreferences.toDTO(): BankingUserPreferencesDTO =
        BankingUserPreferencesDTO(ruleCreationMode = ruleCreationMode)
}
