package io.github.salomax.neotool.banking.service.pendingaction

import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class AccountPendingActionService(
    private val repository: AccountPendingActionRepository,
    private val holdingRepository: HoldingRepository,
) {
    fun listForAccount(
        accountId: UUID,
        status: AccountPendingActionStatus?,
    ): List<AccountPendingAction> {
        val actions = repository.findByAccountAndStatus(accountId, status)
        return actions.filter { action ->
            if (action.entityType != "HOLDING") return@filter true
            val eid = action.entityId ?: return@filter true
            holdingRepository.findById(eid, accountId) != null
        }
    }

    fun dismiss(
        id: UUID,
        accountId: UUID,
    ): AccountPendingAction {
        val action =
            repository.findById(id, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.PENDING_ACTION_NOT_FOUND,
                    field = "id",
                    parameters = mapOf("reason" to "Pending action not found"),
                )

        if (action.entityType == "HOLDING") {
            action.entityId?.let { eid ->
                if (holdingRepository.findById(eid, accountId) == null) {
                    throw ValidationException(
                        BankingErrorCode.PENDING_ACTION_FORBIDDEN,
                        field = "id",
                        parameters = mapOf("reason" to "Pending action is not visible for this account"),
                    )
                }
            }
        }

        if (action.status != AccountPendingActionStatus.PENDING) {
            throw ValidationException(
                BankingErrorCode.PENDING_ACTION_INVALID_STATE,
                field = "status",
                parameters = mapOf("current" to action.status.name),
            )
        }

        val now = Instant.now()
        return repository.update(
            action.copy(
                status = AccountPendingActionStatus.DISMISSED,
                dismissedAt = now,
                updatedAt = now,
            ),
        )
    }
}
