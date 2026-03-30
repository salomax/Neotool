package io.github.salomax.neotool.banking.graphql.preferences.dto

import io.github.salomax.neotool.banking.domain.preferences.RuleApplicationMode
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus

data class AccountPreferencesDTO(
    val ruleApplicationMode: RuleApplicationMode,
    val defaultTransactionStatus: TransactionStatus,
)

