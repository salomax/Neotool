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
        firstDayOfWeek?.let {
            if (it !in 1..7) {
                throw ValidationException(
                    errorCode = CommonErrorCode.INVALID_INPUT,
                    field = "firstDayOfWeek",
                    parameters = mapOf("reason" to "must be between 1 and 7"),
                )
            }
        }

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
                language = language?.let { parseEnumValue<Language>("language", it) } ?: base.language,
                theme = theme?.let { parseEnumValue<Theme>("theme", it) } ?: base.theme,
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

    private inline fun <reified T : Enum<T>> parseEnumValue(
        field: String,
        rawValue: String,
    ): T =
        enumValues<T>().firstOrNull { it.name == rawValue }
            ?: throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = field,
                parameters = mapOf("value" to rawValue),
            )
}
