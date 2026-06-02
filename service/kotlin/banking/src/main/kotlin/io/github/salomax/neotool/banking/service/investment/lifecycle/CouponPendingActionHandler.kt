package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.CouponFrequency
import io.github.salomax.neotool.banking.domain.investment.CouponScheduleService
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType.INTEREST_PAYMENT
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.micronaut.transaction.TransactionDefinition.Propagation
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Singleton
open class CouponPendingActionHandler(
    private val pendingActionRepository: AccountPendingActionRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val couponScheduleService: CouponScheduleService,
    private val assetRepository: AssetRepository,
) : InvestmentTransactionSideEffectHandler {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) {
        syncCouponPendingAction(
            holding = context.holding,
            savedTx = context.transaction,
            assetType = context.asset.assetType,
            accountId = context.accountId,
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(
        context: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    ) {
        if (context.command == null) {
            syncCouponPendingAction(
                holding = context.holding,
                savedTx = null,
                assetType = context.asset.assetType,
                accountId = context.accountId,
            )
            return
        }

        syncCouponPendingAction(
            holding = context.holding,
            savedTx = context.transaction,
            assetType = context.asset.assetType,
            accountId = context.accountId,
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDeleteBatch(contexts: List<InvestmentTransactionContext>) {
    }

    private fun syncCouponPendingAction(
        holding: Holding,
        savedTx: InvestmentTransaction?,
        assetType: AssetType?,
        accountId: UUID,
    ) {
        val holdingId = holding.id ?: return
        if (!holding.supportsCouponPendingAction()) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            )
            return
        }

        if (savedTx?.type == INTEREST_PAYMENT) {
            completeCurrentCouponPendingAction(holding, savedTx.id)
        }

        upsertNextCouponPendingActionIfNeeded(
            holding = holding,
            assetType = assetType,
            today = savedTx?.tradeDate ?: LocalDate.now(),
        )
    }

    internal fun upsertNextCouponPendingActionIfNeeded(
        holding: Holding,
        assetType: AssetType? = null,
        today: LocalDate = LocalDate.now(),
    ) {
        val holdingId = holding.id ?: return
        if (!holding.supportsCouponPendingAction()) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            )
            return
        }

        val transactions = investmentTransactionRepository.findByHolding(holdingId, holding.accountId)
        val lastInterestPaymentDate =
            transactions
                .asSequence()
                .filter { tx -> tx.type == INTEREST_PAYMENT }
                .map { it.tradeDate }
                .maxOrNull()
        val resolvedAssetType =
            assetType ?: assetRepository.findById(holding.assetId)?.assetType
                ?: return
        val schedule =
            couponScheduleService
                .generateSchedule(holding, resolvedAssetType, emptyList(), today)
                .map { it.scheduledDate }
        val nextCouponDate =
            if (lastInterestPaymentDate != null) {
                schedule.firstOrNull { date -> date.isAfter(lastInterestPaymentDate) }
            } else {
                schedule.firstOrNull()
            }

        if (nextCouponDate == null) {
            pendingActionRepository.cancelByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            )
            return
        }

        val visibleFrom = nextCouponDate.minusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant()
        val now = Instant.now()
        val payload =
            mapOf(
                "couponDate" to nextCouponDate.toString(),
                "couponFrequency" to holding.couponFrequency?.name,
            )

        val existing =
            pendingActionRepository.findPendingByEntityAndType(
                "HOLDING",
                holdingId,
                PendingActionType.COUPON_PAYMENT_DUE,
            )
        if (existing != null) {
            if (existing.visibleFrom != visibleFrom || existing.payload != payload) {
                pendingActionRepository.update(
                    existing.copy(
                        visibleFrom = visibleFrom,
                        payload = payload,
                        updatedAt = now,
                    ),
                )
            }
            return
        }

        pendingActionRepository.save(
            AccountPendingAction(
                accountId = holding.accountId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
                severity = PendingActionSeverity.ACTION_REQUIRED,
                status = AccountPendingActionStatus.PENDING,
                entityType = "HOLDING",
                entityId = holdingId,
                relatedTransactionId = null,
                payload = payload,
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

    internal fun completeCurrentCouponPendingAction(holding: Holding) {
        completeCurrentCouponPendingAction(holding, null)
    }

    private fun completeCurrentCouponPendingAction(
        holding: Holding,
        relatedTransactionId: UUID?,
    ) {
        val holdingId = holding.id ?: return
        val existing =
            pendingActionRepository.findPendingByEntityAndType(
                entityType = "HOLDING",
                entityId = holdingId,
                type = PendingActionType.COUPON_PAYMENT_DUE,
            ) ?: return
        val now = Instant.now()
        pendingActionRepository.update(
            existing.copy(
                status = AccountPendingActionStatus.COMPLETED,
                relatedTransactionId = relatedTransactionId ?: existing.relatedTransactionId,
                completedAt = now,
                updatedAt = now,
            ),
        )
    }
}

private fun Holding.supportsCouponPendingAction(): Boolean =
    quantity > BigDecimal.ZERO &&
        maturityDate != null &&
        issueDate != null &&
        couponFrequency != null &&
        couponFrequency != CouponFrequency.AT_MATURITY
