package io.github.salomax.neotool.security.domain.account

import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import java.time.Instant
import java.util.UUID

/**
 * Domain type for an account (ownership boundary for resources).
 * Mirrors [AccountEntity] for use in service layer and API.
 */
data class Account(
    val id: UUID? = null,
    val accountName: String,
    val accountType: AccountType,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val ownerUserId: UUID? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
)
