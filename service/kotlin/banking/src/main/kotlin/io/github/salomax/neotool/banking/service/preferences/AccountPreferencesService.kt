package io.github.salomax.neotool.banking.service.preferences

import io.github.salomax.neotool.banking.domain.preferences.AccountPreferences
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.preferences.AccountPreferencesRepository
import io.github.salomax.neotool.banking.model.preferences.AccountPreferencesMapper
import io.github.salomax.neotool.banking.model.preferences.AccountPreferencesEntity
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Singleton
open class AccountPreferencesService(
    private val repository: AccountPreferencesRepository,
    private val mapper: AccountPreferencesMapper,
) {
    fun get(accountId: UUID): AccountPreferences {
        val entity = repository.findById(accountId).orElse(null) ?: return AccountPreferences.defaultsFor(accountId)
        return mapper.toDomain(entity)
    }

    @Transactional
    open fun update(
        accountId: UUID,
        principal: RequestPrincipal,
        defaultTransactionStatus: TransactionStatus?,
    ): AccountPreferences {
        if (!principal.isOwnerOfAccount(accountId)) {
            throw BusinessRuleException(BankingErrorCode.ACCOUNT_PREFERENCES_OWNER_REQUIRED)
        }

        if (defaultTransactionStatus == null) {
            throw ValidationException(CommonErrorCode.VALIDATION_ERROR, field = "input")
        }

        if (defaultTransactionStatus == TransactionStatus.FUTURE) {
            throw ValidationException(
                CommonErrorCode.VALIDATION_ERROR,
                field = "defaultTransactionStatus",
            )
        }

        val now = Instant.now()
        val existingEntity = repository.findById(accountId).orElse(null)
        val base =
            if (existingEntity != null) {
                mapper.toDomain(existingEntity)
            } else {
                AccountPreferences.defaultsFor(accountId, now)
            }

        val patched =
            base.copy(
                defaultTransactionStatus = defaultTransactionStatus ?: base.defaultTransactionStatus,
                updatedAt = now,
            )

        val saved =
            if (existingEntity != null) {
                mapper.applyToEntity(patched, existingEntity)
                repository.update(existingEntity)
            } else {
                val entity =
                    AccountPreferencesEntity(
                        accountId = accountId,
                        defaultTransactionStatus = patched.defaultTransactionStatus,
                        createdAt = now,
                        updatedAt = now,
                    )
                repository.save(entity)
            }

        return mapper.toDomain(saved)
    }
}
