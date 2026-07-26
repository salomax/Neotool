package io.github.salomax.neotool.banking.graphql.pendingaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.graphql.investment.InvestmentTransactionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import java.time.Instant
import java.util.UUID

data class AccountPendingActionDTO(
    val id: UUID,
    val type: String,
    val severity: String,
    val status: String,
    val entityType: String?,
    val entityId: UUID?,
    val payload: Map<String, Any?>,
    val createdAt: Instant,
    val expiresAt: Instant?,
)

data class ConfirmHoldingMaturityResultDTO(
    val maturityTransaction: InvestmentTransactionDTO,
    val bankingTransaction: TransactionDTO?,
    val pendingAction: AccountPendingActionDTO,
)

data class ConfirmPendingActionResultDTO(
    val pendingAction: AccountPendingActionDTO,
)

fun AccountPendingAction.toDto(): AccountPendingActionDTO =
    AccountPendingActionDTO(
        id = requireNotNull(id),
        type = type.name,
        severity = severity.name,
        status = status.name,
        entityType = entityType,
        entityId = entityId,
        payload = payload,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )
