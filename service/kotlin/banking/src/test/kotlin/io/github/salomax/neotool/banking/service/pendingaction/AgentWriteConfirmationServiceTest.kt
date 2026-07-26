package io.github.salomax.neotool.banking.service.pendingaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.AgentWriteOrigin
import io.github.salomax.neotool.banking.domain.pendingaction.DeleteTransactionConfirmationPayload
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class AgentWriteConfirmationServiceTest {
    private val repository: AccountPendingActionRepository = mock()
    private val transactionService: TransactionService = mock()
    private val transactionRuleService: TransactionRuleService = mock()
    private val service = AgentWriteConfirmationService(repository, transactionService, transactionRuleService)

    @Test
    fun `confirm pending delete dispatches delete with original MCP provenance`() {
        val accountId = UUID.randomUUID()
        val txId = UUID.randomUUID()
        val toolCallId = UUID.randomUUID()
        val pending =
            AccountPendingAction(
                id = UUID.randomUUID(),
                accountId = accountId,
                type = PendingActionType.AGENT_WRITE_CONFIRMATION,
                severity = PendingActionSeverity.ACTION_REQUIRED,
                status = AccountPendingActionStatus.PENDING,
                entityType = "DeleteTransactionConfirmationPayload",
                entityId = txId,
                relatedTransactionId = null,
                payload =
                    DeleteTransactionConfirmationPayload(
                        transactionId = txId,
                        origin = AgentWriteOrigin("mcp-client", toolCallId, "token-1"),
                    ).toPayloadMap(),
                visibleFrom = Instant.now(),
                notifiedAt = null,
                dismissedAt = null,
                completedAt = null,
                cancelledAt = null,
                expiresAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        whenever(repository.findById(eq(pending.id!!), eq(accountId))).thenReturn(pending)
        whenever(transactionService.delete(eq(txId), eq(accountId), anyOrNull())).thenReturn(true)
        whenever(repository.update(anyOrNull())).thenAnswer { it.arguments[0] as AccountPendingAction }

        val result =
            service.confirmPendingAction(
                id = pending.id!!,
                accountId = accountId,
                principal = userPrincipal(accountId),
            )

        val contextCaptor = argumentCaptor<AgentCallContext>()
        verify(transactionService).delete(eq(txId), eq(accountId), contextCaptor.capture())
        verify(transactionRuleService, never()).applyToHistorical(anyOrNull(), anyOrNull(), anyOrNull())
        assertThat(contextCaptor.firstValue.originClientId).isEqualTo("mcp-client")
        assertThat(contextCaptor.firstValue.toolCallId).isEqualTo(toolCallId)
        assertThat(contextCaptor.firstValue.originTokenId).isEqualTo("token-1")
        assertThat(result.pendingAction.status).isEqualTo("COMPLETED")
    }

    @Test
    fun `service principals cannot confirm pending actions`() {
        val accountId = UUID.randomUUID()

        assertThrows<io.github.salomax.neotool.common.error.ValidationException> {
            service.confirmPendingAction(
                id = UUID.randomUUID(),
                accountId = accountId,
                principal = servicePrincipal(accountId),
            )
        }

        verifyNoInteractions(repository, transactionService, transactionRuleService)
    }

    private fun userPrincipal(accountId: UUID) =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = UUID.randomUUID(),
            serviceId = null,
            token = "token",
            tokenId = "token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    private fun servicePrincipal(accountId: UUID) =
        RequestPrincipal(
            principalType = PrincipalType.SERVICE,
            userId = null,
            serviceId = UUID.randomUUID(),
            token = "token",
            tokenId = "token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )
}
