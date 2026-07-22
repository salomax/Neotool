package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.model.OAuth2ConsentEntity
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.OAuth2ConsentRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

data class OAuth2ConsentRequirement(
    val accountId: UUID,
    val accountLabel: String,
    val existingScopes: Set<String>,
    val scopesNeedingConsent: Set<String>,
) {
    val requiresPrompt: Boolean = scopesNeedingConsent.isNotEmpty()
}

@Singleton
open class OAuth2ConsentService(
    private val consentRepository: OAuth2ConsentRepository,
    private val accountMembershipRepository: AccountMembershipRepository,
    private val accountRepository: AccountRepository,
) {
    fun consentRequirement(
        userId: UUID,
        clientId: String,
        requestedScopes: Collection<String>,
    ): OAuth2ConsentRequirement {
        val accountId = currentAccountId(userId)
        val existing = activeConsent(userId, clientId, accountId)
        val existingScopes = existing?.grantedScopes?.let(::parseScopes).orEmpty()
        val requested = requestedScopes.toSet()
        return OAuth2ConsentRequirement(
            accountId = accountId,
            accountLabel = accountLabel(accountId),
            existingScopes = existingScopes,
            scopesNeedingConsent = requested - existingScopes,
        )
    }

    @Transactional
    open fun persistGrant(
        userId: UUID,
        clientId: String,
        accountId: UUID,
        scopes: Collection<String>,
    ): OAuth2ConsentEntity {
        val now = Instant.now()
        val normalizedScopes = scopes.filter { it.isNotBlank() }.distinct().sorted().joinToString(" ")
        val existing = activeConsent(userId, clientId, accountId)
        if (existing != null) {
            existing.grantedScopes = normalizedScopes
            existing.lastGrantedAt = now
            existing.updatedAt = now
            return consentRepository.update(existing)
        }
        return consentRepository.save(
            OAuth2ConsentEntity(
                userId = userId,
                clientId = clientId,
                accountId = accountId,
                grantedScopes = normalizedScopes,
                firstGrantedAt = now,
                lastGrantedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun activeConsent(
        userId: UUID,
        clientId: String,
        accountId: UUID,
    ): OAuth2ConsentEntity? =
        consentRepository.findOneByUserIdAndClientIdAndAccountIdAndRevokedAtIsNull(
            userId,
            clientId,
            accountId,
        ).orElse(null)

    private fun currentAccountId(userId: UUID): UUID =
        accountMembershipRepository
            .findOneByUserIdAndMembershipStatusAndIsDefaultTrue(userId, MembershipStatus.ACTIVE)
            .orElseGet {
                accountMembershipRepository
                    .findByUserIdAndMembershipStatus(userId, MembershipStatus.ACTIVE)
                    .firstOrNull()
                    ?: throw OAuth2InvalidRequestException("User has no active account")
            }.accountId

    private fun accountLabel(accountId: UUID): String =
        accountRepository.findById(accountId).orElse(null)?.accountName ?: accountId.toString()

    private fun parseScopes(scopes: String): Set<String> =
        scopes
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
}
