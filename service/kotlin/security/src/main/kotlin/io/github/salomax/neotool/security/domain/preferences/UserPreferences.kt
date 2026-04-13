package io.github.salomax.neotool.security.domain.preferences

import java.time.Instant
import java.util.UUID

data class UserPreferences(
    val userId: UUID,
    val language: Language,
    val theme: Theme,
    val blurBalances: Boolean,
    val firstDayOfWeek: Int,
    val keepMeConnected: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun defaultsFor(
            userId: UUID,
            now: Instant = Instant.now(),
            keepMeConnected: Boolean = true,
        ): UserPreferences =
            UserPreferences(
                userId = userId,
                language = Language.PORTUGUESE,
                theme = Theme.SYSTEM,
                blurBalances = false,
                firstDayOfWeek = 1,
                keepMeConnected = keepMeConnected,
                createdAt = now,
                updatedAt = now,
            )
    }
}
