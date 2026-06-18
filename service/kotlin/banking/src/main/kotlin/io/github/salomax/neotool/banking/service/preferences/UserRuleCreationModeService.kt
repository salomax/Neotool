package io.github.salomax.neotool.banking.service.preferences

import io.github.salomax.neotool.banking.domain.preferences.BankingUserPreferences
import io.github.salomax.neotool.banking.domain.preferences.RuleCreationMode
import io.github.salomax.neotool.banking.model.preferences.UserRuleCreationModeEntity
import io.github.salomax.neotool.banking.repo.preferences.UserRuleCreationModeRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class UserRuleCreationModeService(
    private val repository: UserRuleCreationModeRepository,
) {
    open fun get(userId: UUID): RuleCreationMode = getPreferences(userId).ruleCreationMode

    open fun getPreferences(userId: UUID): BankingUserPreferences {
        val entity = repository.findById(userId).orElse(null) ?: return BankingUserPreferences.defaultsFor(userId)
        return BankingUserPreferences(
            userId = entity.userId,
            ruleCreationMode = entity.ruleCreationMode,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    @Transactional
    open fun update(
        userId: UUID,
        ruleCreationMode: RuleCreationMode,
    ): BankingUserPreferences {
        val now = Instant.now()
        val existing = repository.findById(userId).orElse(null)
        val saved =
            if (existing != null) {
                existing.ruleCreationMode = ruleCreationMode
                existing.updatedAt = now
                repository.update(existing)
            } else {
                repository.save(
                    UserRuleCreationModeEntity(
                        userId = userId,
                        ruleCreationMode = ruleCreationMode,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

        return BankingUserPreferences(
            userId = saved.userId,
            ruleCreationMode = saved.ruleCreationMode,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt,
        )
    }
}
