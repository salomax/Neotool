package io.github.salomax.neotool.banking.service.preferences

import io.github.salomax.neotool.banking.domain.preferences.BankingUserPreferences
import io.github.salomax.neotool.banking.domain.preferences.RuleApplicationMode
import io.github.salomax.neotool.banking.model.preferences.UserRuleApplicationModeEntity
import io.github.salomax.neotool.banking.repo.preferences.UserRuleApplicationModeRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class UserRuleApplicationModeService(
    private val repository: UserRuleApplicationModeRepository,
) {
    open fun get(userId: UUID): RuleApplicationMode =
        getPreferences(userId).ruleApplicationMode

    open fun getPreferences(userId: UUID): BankingUserPreferences {
        val entity = repository.findById(userId).orElse(null) ?: return BankingUserPreferences.defaultsFor(userId)
        return BankingUserPreferences(
            userId = entity.userId,
            ruleApplicationMode = entity.ruleApplicationMode,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    @Transactional
    open fun update(
        userId: UUID,
        ruleApplicationMode: RuleApplicationMode,
    ): BankingUserPreferences {
        val now = Instant.now()
        val existing = repository.findById(userId).orElse(null)
        val saved =
            if (existing != null) {
                existing.ruleApplicationMode = ruleApplicationMode
                existing.updatedAt = now
                repository.update(existing)
            } else {
                repository.save(
                    UserRuleApplicationModeEntity(
                        userId = userId,
                        ruleApplicationMode = ruleApplicationMode,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

        return BankingUserPreferences(
            userId = saved.userId,
            ruleApplicationMode = saved.ruleApplicationMode,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt,
        )
    }
}
