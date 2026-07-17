package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.ComeCotasInlineBackfillRequestedEvent
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.service.investment.ComeCotasEligibility
import io.github.salomax.neotool.banking.service.investment.ComeCotasSupport
import io.micronaut.context.annotation.Value
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.transaction.TransactionDefinition.Propagation
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class ComeCotasPurchaseBackfillHandler(
    private val jobRepository: ComeCotasCheckpointJobRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val fundMarketDataProvider: FundMarketDataProvider,
    private val inlineBackfillRequestedEventPublisher: ApplicationEventPublisher<ComeCotasInlineBackfillRequestedEvent>,
    @param:Value("\${banking.come-cotas.inline-max-checkpoints:2}")
    private val inlineMaxCheckpoints: Int,
    @param:Value("\${banking.come-cotas.inline-max-age-days:370}")
    private val inlineMaxAgeDays: Long,
) : InvestmentTransactionSideEffectHandler {
    private val logger = KotlinLogging.logger {}

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) {
        enqueueRetroactiveCheckpoints(context)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(
        context: InvestmentTransactionContext,
        previous: InvestmentTransaction,
    ) {
        enqueueRetroactiveCheckpoints(context)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) {
    }

    private fun enqueueRetroactiveCheckpoints(context: InvestmentTransactionContext) {
        val savedTx = context.transaction ?: return
        val holdingId = context.holding.id ?: return
        val backfillDecision = evaluateBackfill(savedTx, context)
        if (!backfillDecision.supported) {
            logger.info {
                "come-cotas backfill skipped holdingId=$holdingId accountId=${context.accountId} " +
                    "tradeDate=${savedTx.tradeDate} reason=${backfillDecision.reason} " +
                    "effectiveTaxProfile=${backfillDecision.effectiveTaxProfile}"
            }
            return
        }

        val today = LocalDate.now()
        if (!savedTx.tradeDate.isBefore(today)) {
            logger.info {
                "come-cotas backfill skipped holdingId=$holdingId accountId=${context.accountId} " +
                    "tradeDate=${savedTx.tradeDate} reason=trade-date-not-in-past"
            }
            return
        }

        val transactions =
            investmentTransactionRepository.findByHolding(holdingId, context.accountId)
                .sortedWith(
                    compareBy<InvestmentTransaction> { it.tradeDate }
                        .thenBy { it.createdAt }
                        .thenBy { it.id.toString() },
                )
        val withholdingDates =
            transactions
                .asSequence()
                .filter { it.type == InvestmentTransactionType.WITHHOLDING_TAX }
                .map { it.tradeDate }
                .toSet()
        val candidateDates =
            ComeCotasSupport.checkpointDatesBetween(savedTx.tradeDate, today)
                .filterNot(withholdingDates::contains)
                .sorted()
        if (candidateDates.isEmpty()) {
            logger.info {
                "come-cotas backfill found no pending checkpoints holdingId=$holdingId " +
                    "accountId=${context.accountId} " +
                    "tradeDate=${savedTx.tradeDate} withholdingDates=$withholdingDates"
            }
            return
        }

        val now = Instant.now()
        val jobIds = candidateDates.map { checkpointDate -> ensurePendingJob(context, checkpointDate, now) }
        val oldestInlineDate = today.minusDays(inlineMaxAgeDays)
        val shouldInline =
            jobIds.size <= inlineMaxCheckpoints &&
                candidateDates.all { !it.isBefore(oldestInlineDate) }

        logger.info {
            "come-cotas backfill enqueued holdingId=$holdingId accountId=${context.accountId} " +
                "tradeDate=${savedTx.tradeDate} fundCnpj=${backfillDecision.fundCnpj} " +
                "providerTaxProfile=${backfillDecision.providerTaxProfile} " +
                "effectiveTaxProfile=${backfillDecision.effectiveTaxProfile} " +
                "candidateDates=$candidateDates jobIds=$jobIds shouldInline=$shouldInline"
        }

        if (shouldInline) {
            inlineBackfillRequestedEventPublisher.publishEvent(
                ComeCotasInlineBackfillRequestedEvent(
                    accountId = context.accountId,
                    jobIds = jobIds,
                    requestedAt = now,
                ),
            )
        }
    }

    private fun supportsBackfill(
        savedTx: InvestmentTransaction,
        context: InvestmentTransactionContext,
    ): Boolean = evaluateBackfill(savedTx, context).supported

    private fun evaluateBackfill(
        savedTx: InvestmentTransaction,
        context: InvestmentTransactionContext,
    ): BackfillDecision {
        if (context.asset.assetType != AssetType.MUTUAL_FUND) {
            return BackfillDecision(supported = false, reason = "asset-type-${context.asset.assetType}")
        }
        if (savedTx.direction != InvestmentTransactionDirection.INFLOW) {
            return BackfillDecision(supported = false, reason = "direction-${savedTx.direction}")
        }
        if (
            savedTx.type !in
            setOf(
                InvestmentTransactionType.PURCHASE,
                InvestmentTransactionType.SUBSCRIPTION,
            )
        ) {
            return BackfillDecision(supported = false, reason = "transaction-type-${savedTx.type}")
        }
        val fundCnpj = context.asset.fundCnpj?.filter(Char::isDigit)?.takeIf { it.length == 14 }
        val snapshotTaxProfile =
            fundCnpj
                ?.let { cnpj ->
                    runCatching { fundMarketDataProvider.getSnapshotsByCnpj(setOf(cnpj))[cnpj]?.taxProfile }
                        .onFailure { error ->
                            logger.warn(error) {
                                "come-cotas backfill could not resolve provider tax profile " +
                                    "fundCnpj=$cnpj holdingId=${context.holding.id} accountId=${context.accountId}"
                            }
                        }.getOrNull()
                }
        val taxProfile =
            ComeCotasEligibility.resolveEffectiveTaxProfile(
                context.holding,
                context.asset,
                snapshotTaxProfile,
            )
        val eligible = ComeCotasEligibility.isEligible(context.holding, context.asset, taxProfile)
        return BackfillDecision(
            supported = eligible,
            reason = if (eligible) "eligible" else "ineligible",
            fundCnpj = fundCnpj,
            providerTaxProfile = snapshotTaxProfile?.name,
            effectiveTaxProfile = taxProfile.name,
        )
    }

    private fun ensurePendingJob(
        context: InvestmentTransactionContext,
        checkpointDate: LocalDate,
        now: Instant,
    ): UUID {
        val holdingId = requireNotNull(context.holding.id)
        val existing = jobRepository.findByHoldingAndCheckpointDate(holdingId, checkpointDate)
        if (existing == null) {
            return requireNotNull(
                jobRepository.save(
                    ComeCotasCheckpointJob(
                        holdingId = holdingId,
                        financialAccountId = context.holding.financialAccountId,
                        accountId = context.accountId,
                        checkpointDate = checkpointDate,
                        status = ComeCotasCheckpointJobStatus.PENDING,
                        createdAt = now,
                        updatedAt = now,
                    ),
                ).id,
            )
        }

        val effectiveJob =
            if (
                existing.status == ComeCotasCheckpointJobStatus.PENDING ||
                existing.status == ComeCotasCheckpointJobStatus.PROCESSING
            ) {
                existing
            } else {
                jobRepository.update(
                    existing.copy(
                        status = ComeCotasCheckpointJobStatus.PENDING,
                        processingNodeId = null,
                        lastHeartbeatAt = null,
                        startedAt = null,
                        completedAt = null,
                        nextAttemptAt = null,
                        errorCode = null,
                        errorDetails = null,
                        updatedAt = now,
                    ),
                )
            }
        return requireNotNull(effectiveJob.id)
    }

    private data class BackfillDecision(
        val supported: Boolean,
        val reason: String,
        val fundCnpj: String? = null,
        val providerTaxProfile: String? = null,
        val effectiveTaxProfile: String? = null,
    )
}
