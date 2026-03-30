package io.github.salomax.neotool.security.model.preferences

import io.github.salomax.neotool.security.domain.preferences.UserPreferences
import jakarta.inject.Singleton

@Singleton
class UserPreferencesMapper {
    fun toDomain(entity: UserPreferencesEntity): UserPreferences =
        UserPreferences(
            userId = entity.userId,
            language = entity.language,
            theme = entity.theme,
            blurBalances = entity.blurBalances,
            firstDayOfWeek = entity.firstDayOfWeek.toInt(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun applyToEntity(
        domain: UserPreferences,
        entity: UserPreferencesEntity,
    ) {
        entity.language = domain.language
        entity.theme = domain.theme
        entity.blurBalances = domain.blurBalances
        entity.firstDayOfWeek = domain.firstDayOfWeek.coerceIn(1, 7).toShort()
        entity.updatedAt = domain.updatedAt
    }
}
