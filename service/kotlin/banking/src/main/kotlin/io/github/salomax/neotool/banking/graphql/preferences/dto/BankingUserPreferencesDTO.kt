package io.github.salomax.neotool.banking.graphql.preferences.dto

import io.github.salomax.neotool.banking.domain.preferences.RuleCreationMode

data class BankingUserPreferencesDTO(
    val ruleCreationMode: RuleCreationMode,
)
