package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.security.domain.AccountManagement
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.graphql.dto.AccountDTO
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.service.management.AccountService
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class AccountResolver(
    private val accountService: AccountService,
    private val accountMembershipRepository: AccountMembershipRepository,
) {
    fun myAccounts(principal: RequestPrincipal): List<AccountDTO> {
        val userId = requireNotNull(principal.userId) { "User ID required" }
        return accountMembershipRepository
            .findByUserIdAndMembershipStatus(userId, MembershipStatus.ACTIVE)
            // Intentionally simple lookup. Typical users have 1-3 memberships, so per-membership
            // account fetch keeps resolver logic clear without meaningful performance impact.
            .mapNotNull { membership ->
                accountService.getById(membership.accountId)?.let { account ->
                    AccountDTO(
                        id = account.id.toString(),
                        accountName = account.accountName,
                        accountType = account.accountType.name,
                        accountStatus = account.accountStatus.name,
                        isOwner = membership.accountRole == AccountRole.OWNER,
                    )
                }
            }
    }

    fun updateAccount(
        accountId: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): AccountDTO {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        val accountName =
            input["accountName"] as? String
                ?: throw ValidationException(
                    errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,
                    field = "accountName",
                )

        val command =
            AccountManagement.UpdateAccountCommand(
                accountId = UUID.fromString(accountId),
                accountName = accountName,
                actorUserId = actorUserId,
            )
        val updated = accountService.update(command)
        val membership =
            accountMembershipRepository
                .findOneByAccountIdAndUserId(updated.id!!, actorUserId)
                .orElseThrow {
                    NotFoundException(
                        errorCode = SecurityErrorCode.ACCOUNT_NOT_FOUND,
                        field = "accountId",
                    )
                }

        return AccountDTO(
            id = updated.id.toString(),
            accountName = updated.accountName,
            accountType = updated.accountType.name,
            accountStatus = updated.accountStatus.name,
            isOwner = membership.accountRole == AccountRole.OWNER,
        )
    }
}
