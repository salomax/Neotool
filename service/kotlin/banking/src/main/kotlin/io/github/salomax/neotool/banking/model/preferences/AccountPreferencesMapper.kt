package io.github.salomax.neotool.banking.model.preferences

import io.github.salomax.neotool.banking.domain.preferences.AccountPreferences
import jakarta.inject.Singleton

@Singleton
class AccountPreferencesMapper {
    fun toDomain(entity: AccountPreferencesEntity): AccountPreferences =
        AccountPreferences(
            accountId = entity.accountId,
            ruleApplicationMode = entity.ruleApplicationMode,
            defaultTransactionStatus = entity.defaultTransactionStatus,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun applyToEntity(
        domain: AccountPreferences,
        entity: AccountPreferencesEntity,
    ) {
        entity.ruleApplicationMode = domain.ruleApplicationMode
        entity.defaultTransactionStatus = domain.defaultTransactionStatus
        entity.updatedAt = domain.updatedAt
    }
}
