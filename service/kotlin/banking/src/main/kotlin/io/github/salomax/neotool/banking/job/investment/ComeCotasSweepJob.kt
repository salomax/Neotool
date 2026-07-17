package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.service.investment.ComeCotasEligibility
import io.github.salomax.neotool.banking.service.investment.ComeCotasSupport
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.LocalDate

@Singleton
@Requires(property = "banking.come-cotas.sweep.enabled", notEquals = "false", defaultValue = "true")
open class ComeCotasSweepJob(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val transactionRepository: InvestmentTransactionRepository,
    private val jobRepository: ComeCotasCheckpointJobRepository,
    private val fundMarketDataProvider: FundMarketDataProvider,
    @param:Value("\${banking.come-cotas.sweep.batch-size:100}")
    private val batchSize: Int,
    @param:Value("\${banking.come-cotas.sweep.since:2024-01-01}")
    private val since: LocalDate,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${banking.come-cotas.sweep.fixed-delay:PT24H}")
    fun sweep() {
        val today = clock()
        val holdings = holdingRepository.findOpenComeCotasCandidates()
        if (holdings.isEmpty()) return

        val assetsById = assetRepository.findByIds(holdings.map { it.assetId }.toSet()).associateBy { it.id!! }
        val cnpjs =
            holdings.mapNotNull { holding ->
                assetsById[holding.assetId]?.fundCnpj?.filter(Char::isDigit)?.takeIf { it.length == 14 }
            }.toSet()
        val snapshotsByCnpj = fundMarketDataProvider.getSnapshotsByCnpj(cnpjs)
        val transactionsByHolding =
            holdings.groupBy { it.accountId }.flatMap { (accountId, scopedHoldings) ->
                transactionRepository.findByHoldingIds(scopedHoldings.mapNotNull { it.id }, accountId)
            }.groupBy { it.holdingId }

        var enqueued = 0
        holdings.forEach { holding ->
            if (enqueued >= batchSize) return@forEach
            val holdingId = holding.id ?: return@forEach
            val asset = assetsById[holding.assetId] ?: return@forEach
            val fundCnpj = asset.fundCnpj?.filter(Char::isDigit)?.takeIf { it.length == 14 } ?: return@forEach
            val taxProfile =
                ComeCotasEligibility.resolveEffectiveTaxProfile(
                    holding,
                    asset,
                    snapshotsByCnpj[fundCnpj]?.taxProfile,
                )
            if (!ComeCotasEligibility.isEligible(holding, asset, taxProfile)) return@forEach

            val transactions =
                transactionsByHolding[holdingId].orEmpty()
                    .sortedWith(compareBy({ it.tradeDate }, { it.createdAt }, { it.id.toString() }))
            if (transactions.isEmpty()) return@forEach
            val firstTradeDate = maxOf(transactions.minOf { it.tradeDate }, since)
            val existingWithholdingDates =
                transactions
                    .filter { it.type.name == "WITHHOLDING_TAX" }
                    .map { it.tradeDate }

            ComeCotasSupport.checkpointDatesBetween(firstTradeDate, today)
                .filter { checkpointDate ->
                    val window = ComeCotasSupport.checkpointWindow(checkpointDate)
                    existingWithholdingDates.none { it in window }
                }
                .sorted()
                .forEach { checkpointDate ->
                    if (enqueued >= batchSize) return@forEach
                    val inserted =
                        jobRepository.saveIfAbsent(
                            ComeCotasCheckpointJob(
                                holdingId = holdingId,
                                financialAccountId = holding.financialAccountId,
                                accountId = holding.accountId,
                                checkpointDate = checkpointDate,
                                status = ComeCotasCheckpointJobStatus.PENDING,
                            ),
                        )
                    if (inserted) {
                        enqueued += 1
                        logger.info {
                            "come-cotas sweep enqueued holdingId=$holdingId accountId=${holding.accountId} " +
                                "checkpointDate=$checkpointDate fundCnpj=$fundCnpj taxProfile=$taxProfile"
                        }
                    }
                }
        }

        if (enqueued > 0) {
            logger.info { "ComeCotasSweepJob enqueued $enqueued checkpoint job(s)" }
        }
    }
}
