package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.security.graphql.dto.UserPreferencesDTO
import io.github.salomax.neotool.security.service.preferences.UserPreferencesService
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class UserPreferencesResolver(
    private val service: UserPreferencesService,
) {
    fun userPreferences(principal: RequestPrincipal): UserPreferencesDTO {
        val userId = requireNotNull(principal.userId) { "User ID required" }
        return service.get(userId).toDTO()
    }

    fun updateUserPreferences(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): UserPreferencesDTO {
        val userId = requireNotNull(principal.userId) { "User ID required" }
        val language = input["language"] as? String
        val theme = input["theme"] as? String
        val blurBalances = input["blurBalances"] as? Boolean
        val firstDayOfWeek = (input["firstDayOfWeek"] as? Number)?.toInt()
        return service.update(userId, language, theme, blurBalances, firstDayOfWeek).toDTO()
    }

    private fun io.github.salomax.neotool.security.domain.preferences.UserPreferences.toDTO(): UserPreferencesDTO =
        UserPreferencesDTO(
            language = language.name,
            theme = theme.name,
            blurBalances = blurBalances,
            firstDayOfWeek = firstDayOfWeek,
        )
}
