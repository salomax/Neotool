package io.github.salomax.neotool.banking.service.pendingaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.AgentWriteConfirmationPayload
import io.github.salomax.neotool.banking.domain.pendingaction.AgentWriteConfirmationPayloads
import io.github.salomax.neotool.banking.domain.pendingaction.AgentWriteOrigin
import io.github.salomax.neotool.banking.domain.pendingaction.ApplyRuleToHistoricalConfirmationPayload
import io.github.salomax.neotool.banking.domain.pendingaction.BulkCategorizeConfirmationPayload
import io.github.salomax.neotool.banking.domain.pendingaction.DeleteTransactionConfirmationPayload
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.pendingaction.bulkCategorizeRequestId
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.graphql.pendingaction.ConfirmPendingActionResultDTO
import io.github.salomax.neotool.banking.graphql.pendingaction.toDto
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class AgentWriteConfirmationService(
    private val repository: AccountPendingActionRepository,
    private val transactionService: TransactionService,
    private val transactionRuleService: TransactionRuleService,
) {
    @Transactional
    open fun requestDeleteTransaction(
        accountId: UUID,
        transactionId: UUID,
        agentCallContext: AgentCallContext,
    ): AccountPendingAction =
        createOrReusePendingAction(
            accountId = accountId,
            entityType = DeleteTransactionConfirmationPayload::class.java.simpleName,
            entityId = transactionId,
            payload =
                DeleteTransactionConfirmationPayload(
                    transactionId = transactionId,
                    origin = agentCallContext.toOrigin(),
                ),
        )

    @Transactional
    open fun requestBulkCategorize(
        accountId: UUID,
        transactionIds: List<UUID>,
        categoryId: UUID,
        agentCallContext: AgentCallContext,
    ): AccountPendingAction {
        val requestId =
            bulkCategorizeRequestId(
                transactionIds = transactionIds,
                categoryId = categoryId,
            )
        return createOrReusePendingAction(
            accountId = accountId,
            entityType = BulkCategorizeConfirmationPayload::class.java.simpleName,
            entityId = requestId,
            payload =
                BulkCategorizeConfirmationPayload(
                    transactionIds = transactionIds,
                    categoryId = categoryId,
                    origin = agentCallContext.toOrigin(),
                ),
        )
    }

    @Transactional
    open fun requestApplyRuleToHistorical(
        accountId: UUID,
        ruleId: UUID,
        agentCallContext: AgentCallContext,
    ): AccountPendingAction =
        createOrReusePendingAction(
            accountId = accountId,
            entityType = ApplyRuleToHistoricalConfirmationPayload::class.java.simpleName,
            entityId = ruleId,
            payload =
                ApplyRuleToHistoricalConfirmationPayload(
                    ruleId = ruleId,
                    origin = agentCallContext.toOrigin(),
                ),
        )

    @Transactional
    open fun confirmPendingAction(
        id: UUID,
        accountId: UUID,
        principal: RequestPrincipal,
    ): ConfirmPendingActionResultDTO {
        if (principal.principalType != PrincipalType.USER) {
            throw ValidationException(
                BankingErrorCode.PENDING_ACTION_FORBIDDEN,
                field = "id",
                parameters = mapOf("reason" to "Only user principals can confirm pending actions"),
            )
        }

        val action =
            repository.findById(id, accountId)
                ?: throw ValidationException(
                    BankingErrorCode.PENDING_ACTION_NOT_FOUND,
                    field = "id",
                    parameters = mapOf("reason" to "Pending action not found"),
                )

        if (action.status != AccountPendingActionStatus.PENDING) {
            throw ValidationException(
                BankingErrorCode.PENDING_ACTION_INVALID_STATE,
                field = "status",
                parameters = mapOf("current" to action.status.name),
            )
        }

        val payload =
            AgentWriteConfirmationPayloads.fromMap(action.payload)
                ?: throw ValidationException(
                    BankingErrorCode.PENDING_ACTION_INVALID_STATE,
                    field = "payload",
                    parameters = mapOf("reason" to "Unsupported pending action payload"),
                )

        executePendingAction(accountId, payload)

        val now = Instant.now()
        val completed =
            repository.update(
                action.copy(
                    status = AccountPendingActionStatus.COMPLETED,
                    completedAt = now,
                    updatedAt = now,
                ),
            )
        return ConfirmPendingActionResultDTO(pendingAction = completed.toDto())
    }

    private fun executePendingAction(
        accountId: UUID,
        payload: AgentWriteConfirmationPayload,
    ) {
        val origin = payload.origin.toAgentCallContext()
        when (payload) {
            is DeleteTransactionConfirmationPayload ->
                transactionService.delete(
                    id = payload.transactionId,
                    accountId = accountId,
                    agentCallContext = origin,
                )

            is BulkCategorizeConfirmationPayload ->
                transactionService.bulkCategorize(
                    transactionIds = payload.transactionIds,
                    categoryId = payload.categoryId,
                    accountId = accountId,
                    agentCallContext = origin,
                )

            is ApplyRuleToHistoricalConfirmationPayload ->
                transactionRuleService.applyToHistorical(
                    ruleId = payload.ruleId,
                    accountId = accountId,
                    agentCallContext = origin,
                )
        }
    }

    private fun createOrReusePendingAction(
        accountId: UUID,
        entityType: String,
        entityId: UUID,
        payload: AgentWriteConfirmationPayload,
    ): AccountPendingAction {
        repository.findPendingByEntityAndType(
            entityType = entityType,
            entityId = entityId,
            type = PendingActionType.AGENT_WRITE_CONFIRMATION,
        )?.let { return it }

        val now = Instant.now()
        return repository.save(
            AccountPendingAction(
                accountId = accountId,
                type = PendingActionType.AGENT_WRITE_CONFIRMATION,
                severity = PendingActionSeverity.ACTION_REQUIRED,
                status = AccountPendingActionStatus.PENDING,
                entityType = entityType,
                entityId = entityId,
                relatedTransactionId = null,
                payload = payload.toPayloadMap(),
                visibleFrom = now,
                notifiedAt = null,
                dismissedAt = null,
                completedAt = null,
                cancelledAt = null,
                expiresAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun AgentCallContext.toOrigin(): AgentWriteOrigin =
        AgentWriteOrigin(
            clientId = originClientId,
            toolCallId = toolCallId,
            tokenId = originTokenId,
        )

    private fun AgentWriteOrigin.toAgentCallContext(): AgentCallContext =
        AgentCallContext(
            toolCallId = toolCallId,
            originClientId = clientId,
            originTokenId = tokenId,
        )
}
