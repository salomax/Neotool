package io.github.salomax.neotool.banking.graphql.preferences.dto

import io.github.salomax.neotool.banking.domain.preferences.RuleApplicationMode

data class BankingUserPreferencesDTO(
    val ruleApplicationMode: RuleApplicationMode,
)
