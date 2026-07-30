package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationJob
import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationStatus
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.investment.TaxRecalculationJobRepository
import io.github.salomax.neotool.banking.service.investment.tax.TaxRegimeRegistry
import io.github.salomax.neotool.common.finance.tax.engine.TaxLot
import io.github.salomax.neotool.common.finance.tax.engine.computeTax
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Provider
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.InetAddress
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class TaxRecalculationService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val checkpointRepository: ComeCotasCheckpointRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val fundMarketDataProvider: FundMarketDataProvider,
    private val confirmWithholdingTaxServiceProvider: Provider<ConfirmWithholdingTaxService>,
    private val portfolioFlowEventService: PortfolioFlowEventService,
    private val taxRecalculationJobRepository: TaxRecalculationJobRepository,
) {
    private val logger = KotlinLogging.logger {}
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    @Transactional
    open fun requestRecalculation(
        holdingId: UUID,
        accountId: UUID,
        recalculateFrom: LocalDate,
        triggeredBy: String,
    ): TaxRecalculationJob {
        val holding =
            holdingRepository.findById(holdingId, accountId)
                ?: throw NoSuchElementException("Holding $holdingId not found")
        val now = Instant.now()
        val existing = taxRecalculationJobRepository.findPendingByHolding(holdingId)

        return if (existing != null) {
            taxRecalculationJobRepository.update(
                existing.copy(
                    recalculateFrom = minOf(existing.recalculateFrom, recalculateFrom),
                    triggeredBy = triggeredBy.take(TRIGGERED_BY_MAX_LENGTH),
                    updatedAt = now,
                ),
            )
        } else {
            val processing = taxRecalculationJobRepository.findProcessingByHolding(holdingId)
            val effectiveFrom =
                if (processing != null) {
                    minOf(recalculateFrom, processing.recalculateFrom)
                } else {
                    recalculateFrom
                }
            taxRecalculationJobRepository.save(
                TaxRecalculationJob(
                    holdingId = holdingId,
                    financialAccountId = holding.financialAccountId,
                    accountId = accountId,
                    status = TaxRecalculationStatus.PENDING,
                    recalculateFrom = effectiveFrom,
                    triggeredBy = triggeredBy.take(TRIGGERED_BY_MAX_LENGTH),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    @Transactional
    open fun requestRecalculationForTransaction(
        holdingId: UUID,
        accountId: UUID,
        tradeDate: LocalDate,
        triggeredBy: String,
    ): TaxRecalculationJob? {
        val holding =
            holdingRepository.findById(holdingId, accountId)
                ?: throw NoSuchElementException("Holding $holdingId not found")
        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NoSuchElementException("Asset ${holding.assetId} not found")
        if (!hasPeriodicWithholding(holding, asset)) return null

        return requestRecalculation(
            holdingId = holdingId,
            accountId = accountId,
            recalculateFrom = ComeCotasSupport.earliestCheckpointOnOrAfter(tradeDate),
            triggeredBy = triggeredBy,
        )
    }

    @Transactional
    open fun process(jobId: UUID) {
        val claimed = taxRecalculationJobRepository.tryClaimJob(jobId, NODE_ID) ?: return
        var checkpointsProcessed = 0

        try {
            val holding =
                holdingRepository.findById(claimed.holdingId, claimed.accountId)
                    ?: run {
                        complete(claimed, checkpointsProcessed)
                        return
                    }
            val asset =
                assetRepository.findById(holding.assetId)
                    ?: throw NoSuchElementException("Asset ${holding.assetId} not found")
            if (!hasPeriodicWithholding(holding, asset)) {
                complete(claimed, checkpointsProcessed)
                return
            }

            checkpointDatesFrom(claimed.recalculateFrom, LocalDate.now()).forEach { checkpointDate ->
                val state = recomputeCheckpointState(claimed, checkpointDate)
                when (state) {
                    is CheckpointState.RetryLater -> throw IllegalStateException(state.reason)
                    is CheckpointState.Skipped -> Unit
                    is CheckpointState.Ready -> {
                        val persisted = state.persistedCheckpoint
                        val persistedComputed =
                            persisted?.computedAmount ?: BigDecimal.ZERO.setScale(
                                2,
                                RoundingMode.HALF_UP,
                            )
                        val persistedWithheld =
                            persisted?.withheldAmount ?: BigDecimal.ZERO.setScale(
                                2,
                                RoundingMode.HALF_UP,
                            )
                        val changed =
                            persistedComputed.compareTo(state.computedAmount) != 0 ||
                                persistedWithheld.compareTo(state.withheldAmount) != 0

                        if (!changed) {
                            checkpointsProcessed += 1
                            return@forEach
                        }

                        confirmWithholdingTaxServiceProvider.get().reconcile(
                            command =
                                ConfirmWithholdingTaxCommand(
                                    holdingId = state.holdingId,
                                    tradeDate = checkpointDate,
                                    irValue = state.withheldAmount,
                                    quotasDeducted = state.quotasDeducted,
                                    source = InvestmentSource.SYSTEM,
                                    providerTransactionId = providerTransactionId(state.holdingId, checkpointDate),
                                ),
                            accountId = claimed.accountId,
                            userId = state.actorUserId,
                        )
                        checkpointRepository.upsert(
                            ComeCotasCheckpoint(
                                holdingId = state.holdingId,
                                checkpointDate = checkpointDate,
                                computedAmount = state.computedAmount,
                                withheldAmount = state.withheldAmount,
                            ),
                        )
                        if (persistedWithheld.compareTo(state.withheldAmount) != 0) {
                            portfolioFlowEventService.requestRecalculation(
                                financialAccountId = state.financialAccountId,
                                accountId = claimed.accountId,
                                recalculateFrom = checkpointDate,
                                triggeredBy = "tax_recalc:$jobId:$checkpointDate",
                            )
                        }
                        checkpointsProcessed += 1
                    }
                }
            }

            complete(claimed, checkpointsProcessed)
        } catch (e: Exception) {
            logger.error(e) { "Tax recalculation failed: jobId=$jobId checkpointsProcessed=$checkpointsProcessed" }
            fail(claimed, checkpointsProcessed, e)
        }
    }

    internal fun hasPeriodicWithholding(
        holding: Holding,
        asset: Asset,
    ): Boolean {
        if (asset.assetType != AssetType.MUTUAL_FUND) return false
        val taxProfile = ComeCotasEligibility.resolveEffectiveTaxProfile(holding, asset, snapshotTaxProfile = null)
        return TaxRegimeRegistry.resolveFund(
            assetType = asset.assetType,
            taxProfile = taxProfile,
            comeCotasEnabled = holding.comeCotasEnabled,
        ).hasPeriodicWithholding
    }

    private fun recomputeCheckpointState(
        job: TaxRecalculationJob,
        checkpointDate: LocalDate,
    ): CheckpointState {
        val holding =
            holdingRepository.findById(job.holdingId, job.accountId)
                ?: return CheckpointState.Skipped
        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NoSuchElementException("Asset ${holding.assetId} not found")
        if (!hasPeriodicWithholding(holding, asset)) return CheckpointState.Skipped

        val fundCnpj =
            asset.fundCnpj?.filter(Char::isDigit)?.takeIf { it.length == 14 }
                ?: throw IllegalArgumentException("asset.fundCnpj is required for holding ${holding.id}")
        val snapshot = fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))[fundCnpj]
        val taxProfile = ComeCotasEligibility.resolveEffectiveTaxProfile(holding, asset, snapshot?.taxProfile)
        val regime =
            TaxRegimeRegistry.resolveFund(
                assetType = asset.assetType,
                taxProfile = taxProfile,
                comeCotasEnabled = holding.comeCotasEnabled,
            )
        if (!regime.hasPeriodicWithholding) return CheckpointState.Skipped

        val holdingId = requireNotNull(holding.id) { "Holding id must not be null" }
        val transactions =
            investmentTransactionRepository.findByHolding(holdingId, job.accountId)
                .sortedWith(
                    compareBy<InvestmentTransaction> { it.tradeDate }
                        .thenBy { it.createdAt }
                        .thenBy { it.id.toString() },
                )
        val actorUserId =
            transactions.maxWithOrNull(
                compareBy<InvestmentTransaction> { it.tradeDate }.thenBy { it.createdAt },
            )?.createdByUserId
                ?: return CheckpointState.Skipped
        val persistedCheckpoint =
            checkpointRepository.findByHoldingAndDateRange(holdingId, checkpointDate, checkpointDate).firstOrNull()
        val transactionsUpToCheckpoint = transactions.filter { !it.tradeDate.isAfter(checkpointDate) }
        if (transactionsUpToCheckpoint.isEmpty()) {
            return CheckpointState.Ready(
                holdingId = holdingId,
                financialAccountId = holding.financialAccountId,
                actorUserId = actorUserId,
                persistedCheckpoint = persistedCheckpoint,
                computedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                withheldAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                quotasDeducted = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP),
            )
        }

        val firstTradeDate = transactionsUpToCheckpoint.minOf { it.tradeDate }
        val navAtCheckpoint =
            fundMarketDataProvider.getNavSeries(fundCnpj, firstTradeDate, checkpointDate)
                .lastOrNull { !it.first.isAfter(checkpointDate) }
                ?: return CheckpointState.RetryLater("NAV unavailable for checkpoint $checkpointDate")
        val openLots = ComeCotasSupport.reconstructOpenLots(transactionsUpToCheckpoint)
        if (openLots.isEmpty()) {
            return CheckpointState.Ready(
                holdingId = holdingId,
                financialAccountId = holding.financialAccountId,
                actorUserId = actorUserId,
                persistedCheckpoint = persistedCheckpoint,
                computedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                withheldAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                quotasDeducted = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP),
            )
        }

        val priorWithheld =
            checkpointRepository.findByHoldingAndDateRange(holdingId, firstTradeDate, checkpointDate)
                .filter { it.checkpointDate.isBefore(checkpointDate) }
                .fold(BigDecimal.ZERO) { acc, checkpoint -> acc.add(checkpoint.withheldAmount, mathContext) }
                .setScale(2, RoundingMode.HALF_UP)
        val computedAmount =
            computeTax(
                lots =
                    openLots.mapIndexed { index, lot ->
                        TaxLot(
                            id = "$holdingId-$index-${lot.tradeDate}",
                            quantity = lot.quantity,
                            unitCost = lot.unitPrice,
                            acquisitionDate = lot.tradeDate,
                        )
                    },
                regime = regime,
                referenceDate = checkpointDate,
            ) { date ->
                if (date == checkpointDate) navAtCheckpoint.second else null
            }.totalPeriodicWithholdingDue.setScale(2, RoundingMode.HALF_UP)
        val withheldAmount =
            computedAmount.subtract(priorWithheld, mathContext)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
        val quotasDeducted =
            if (withheldAmount > BigDecimal.ZERO) {
                withheldAmount.divide(navAtCheckpoint.second, 8, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP)
            }

        return CheckpointState.Ready(
            holdingId = holdingId,
            financialAccountId = holding.financialAccountId,
            actorUserId = actorUserId,
            persistedCheckpoint = persistedCheckpoint,
            computedAmount = computedAmount,
            withheldAmount = withheldAmount,
            quotasDeducted = quotasDeducted,
        )
    }

    private fun checkpointDatesFrom(
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> = ComeCotasSupport.checkpointDatesBetween(from.minusDays(1), to).filter { !it.isBefore(from) }

    private fun providerTransactionId(
        holdingId: UUID,
        checkpointDate: LocalDate,
    ): String = "comecotas:$holdingId:$checkpointDate"

    private fun complete(
        job: TaxRecalculationJob,
        checkpointsProcessed: Int,
    ) {
        taxRecalculationJobRepository.update(
            job.copy(
                status = TaxRecalculationStatus.COMPLETED,
                daysProcessed = checkpointsProcessed,
                processingNodeId = null,
                lastHeartbeatAt = null,
                errorCode = null,
                errorDetails = null,
                completedAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun fail(
        job: TaxRecalculationJob,
        checkpointsProcessed: Int,
        cause: Throwable,
    ) {
        taxRecalculationJobRepository.update(
            job.copy(
                status = TaxRecalculationStatus.FAILED,
                daysProcessed = checkpointsProcessed,
                processingNodeId = null,
                lastHeartbeatAt = null,
                errorCode = cause.javaClass.simpleName,
                errorDetails = cause.message?.take(MAX_ERROR_DETAILS_LENGTH),
                completedAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
    }

    private sealed interface CheckpointState {
        data object Skipped : CheckpointState

        data class RetryLater(
            val reason: String,
        ) : CheckpointState

        data class Ready(
            val holdingId: UUID,
            val financialAccountId: UUID,
            val actorUserId: UUID,
            val persistedCheckpoint: ComeCotasCheckpoint?,
            val computedAmount: BigDecimal,
            val withheldAmount: BigDecimal,
            val quotasDeducted: BigDecimal,
        ) : CheckpointState
    }

    private companion object {
        const val TRIGGERED_BY_MAX_LENGTH = 100
        const val MAX_ERROR_DETAILS_LENGTH = 2000
        val NODE_ID: String =
            runCatching { InetAddress.getLocalHost().hostName }
                .getOrDefault(UUID.randomUUID().toString())
    }
}
