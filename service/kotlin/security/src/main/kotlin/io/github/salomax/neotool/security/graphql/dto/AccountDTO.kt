package io.github.salomax.neotool.security.graphql.dto

data class AccountDTO(
    val id: String,
    val accountName: String,
    val accountType: String,
    val accountStatus: String,
    val isOwner: Boolean,
)
