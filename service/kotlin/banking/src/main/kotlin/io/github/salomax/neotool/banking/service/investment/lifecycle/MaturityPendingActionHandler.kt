package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.micronaut.transaction.TransactionDefinition.Propagation
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Singleton
open class MaturityPendingActionHandler(
    private val pendingActionRepository: AccountPendingActionRepository,
) : InvestmentTransactionSideEffectHandler {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) {
        syncMaturityPendingAction(context.holding, context.accountId)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(
        context: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    ) {
        syncMaturityPendingAction(context.holding, context.accountId)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDeleteBatch(contexts: List<InvestmentTransactionContext>) {
    }

    private fun syncMaturityPendingAction(
        holding: Holding,
        accountId: UUID,
    ) {
        val holdingId = holding.id ?: return
        val maturityDate = holding.maturityDate

        if (maturityDate == null || holding.quantity <= BigDecimal.ZERO) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.INVESTMENT_MATURED,
            )
            return
        }

        val visibleFrom =
            maturityDate
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        val now = Instant.now()

        val existing =
            pendingActionRepository.findPendingByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.INVESTMENT_MATURED,
            )

        if (existing != null) {
            if (existing.visibleFrom != visibleFrom) {
                pendingActionRepository.update(
                    existing.copy(
                        visibleFrom = visibleFrom,
                        updatedAt = now,
                    ),
                )
            }
        } else {
            pendingActionRepository.save(
                AccountPendingAction(
                    accountId = accountId,
                    type = PendingActionType.INVESTMENT_MATURED,
                    severity = PendingActionSeverity.ACTION_REQUIRED,
                    status = AccountPendingActionStatus.PENDING,
                    entityType = "HOLDING",
                    entityId = holdingId,
                    relatedTransactionId = null,
                    payload =
                        mapOf(
                            "assetName" to holding.assetId.toString(),
                            "maturityDate" to maturityDate.toString(),
                        ),
                    visibleFrom = visibleFrom,
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
    }
}
