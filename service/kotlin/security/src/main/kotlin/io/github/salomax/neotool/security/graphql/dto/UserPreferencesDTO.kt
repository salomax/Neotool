package io.github.salomax.neotool.security.graphql.dto

data class UserPreferencesDTO(
    val language: String,
    val theme: String,
    val blurBalances: Boolean,
    val firstDayOfWeek: Int,
    val keepMeConnected: Boolean,
)
