package io.github.salomax.neotool.security.service.preferences

import io.github.salomax.neotool.security.domain.preferences.Language
import io.github.salomax.neotool.security.domain.preferences.Theme
import io.github.salomax.neotool.security.domain.preferences.UserPreferences
import io.github.salomax.neotool.security.model.preferences.UserPreferencesEntity
import io.github.salomax.neotool.security.model.preferences.UserPreferencesMapper
import io.github.salomax.neotool.security.repo.UserPreferencesRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@Singleton
open class UserPreferencesService(
    private val repository: UserPreferencesRepository,
    private val mapper: UserPreferencesMapper,
) {
    fun get(userId: UUID): UserPreferences {
        val row = repository.findById(userId).orElse(null) ?: return UserPreferences.defaultsFor(userId)
        return mapper.toDomain(row)
    }

    @Transactional
    open fun update(
        userId: UUID,
        language: String?,
        theme: String?,
        blurBalances: Boolean?,
        firstDayOfWeek: Int?,
    ): UserPreferences {
        if (language == null && theme == null && blurBalances == null && firstDayOfWeek == null) {
            throw ValidationException(CommonErrorCode.VALIDATION_ERROR, field = "input")
        }
        firstDayOfWeek?.let { require(it in 1..7) { "firstDayOfWeek must be between 1 and 7" } }

        val now = Instant.now()
        val existing = repository.findById(userId).orElse(null)
        val base =
            if (existing != null) {
                mapper.toDomain(existing)
            } else {
                UserPreferences.defaultsFor(userId, now)
            }

        val patched =
            base.copy(
                language = language?.let { Language.valueOf(it) } ?: base.language,
                theme = theme?.let { Theme.valueOf(it) } ?: base.theme,
                blurBalances = blurBalances ?: base.blurBalances,
                firstDayOfWeek = firstDayOfWeek ?: base.firstDayOfWeek,
                updatedAt = now,
            )

        val entity =
            if (existing != null) {
                mapper.applyToEntity(patched, existing)
                existing
            } else {
                UserPreferencesEntity(
                    userId = userId,
                    language = patched.language,
                    theme = patched.theme,
                    blurBalances = patched.blurBalances,
                    firstDayOfWeek = patched.firstDayOfWeek.coerceIn(1, 7).toShort(),
                    createdAt = now,
                    updatedAt = now,
                )
            }
        return mapper.toDomain(repository.save(entity))
    }
}
