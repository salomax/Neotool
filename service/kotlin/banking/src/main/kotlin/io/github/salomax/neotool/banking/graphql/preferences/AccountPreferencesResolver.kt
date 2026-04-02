package io.github.salomax.neotool.banking.graphql.preferences

import io.github.salomax.neotool.banking.domain.preferences.AccountPreferences
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.graphql.parseEnumValue
import io.github.salomax.neotool.banking.graphql.preferences.dto.AccountPreferencesDTO
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class AccountPreferencesResolver(
    private val service: AccountPreferencesService,
) {
    fun accountPreferences(principal: RequestPrincipal): AccountPreferencesDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return service.get(accountId).toDTO()
    }

    fun updateAccountPreferences(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): AccountPreferencesDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val defaultTransactionStatus =
            (input["defaultTransactionStatus"] as? String)?.let {
                parseEnumValue<TransactionStatus>("defaultTransactionStatus", it)
            }

        return service.update(
            accountId = accountId,
            principal = principal,
            defaultTransactionStatus = defaultTransactionStatus,
        ).toDTO()
    }

    private fun AccountPreferences.toDTO(): AccountPreferencesDTO =
        AccountPreferencesDTO(
            defaultTransactionStatus = defaultTransactionStatus,
        )
}
