package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
class ComeCotasCheckpointService(
    private val jobRepository: ComeCotasCheckpointJobRepository,
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val fundMarketDataProvider: FundMarketDataProvider,
    private val confirmWithholdingTaxService: ConfirmWithholdingTaxService,
    @param:Value("\${banking.come-cotas.retry.max-attempts:5}")
    private val maxAttempts: Int,
    @param:Value("\${banking.come-cotas.retry.base-delay:PT6H}")
    private val retryBaseDelay: Duration,
) {
    private val logger = KotlinLogging.logger {}
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun process(jobId: UUID) {
        val claimed = jobRepository.tryClaimJob(jobId, NODE_ID) ?: return
        logger.info {
            "come-cotas checkpoint claimed jobId=${claimed.id} holdingId=${claimed.holdingId} " +
                "accountId=${claimed.accountId} checkpointDate=${claimed.checkpointDate} " +
                "attempt=${claimed.attemptCount}"
        }
        try {
            when (val outcome = handle(claimed)) {
                is ProcessingOutcome.Completed -> {
                    logger.info {
                        "come-cotas checkpoint completed jobId=${claimed.id} holdingId=${claimed.holdingId} " +
                            "checkpointDate=${claimed.checkpointDate}"
                    }
                    complete(claimed)
                }
                is ProcessingOutcome.Requeue -> {
                    logger.info {
                        "come-cotas checkpoint requeue jobId=${claimed.id} holdingId=${claimed.holdingId} " +
                            "checkpointDate=${claimed.checkpointDate} delay=${outcome.delay} " +
                            "errorCode=${outcome.errorCode} errorDetails=${outcome.errorDetails}"
                    }
                    requeue(claimed, outcome.errorCode, outcome.errorDetails, outcome.delay)
                }
            }
        } catch (e: DomainException) {
            logger.error(e) { "Come-cotas checkpoint failed: jobId=$jobId code=${e.errorCode.code}" }
            fail(claimed, e.errorCode.code, e.message)
        } catch (e: Exception) {
            logger.error(e) { "Come-cotas checkpoint failed: jobId=$jobId cause=${e.message}" }
            retryOrFail(claimed, BankingErrorCode.PORTFOLIO_RECALCULATION_FAILED.code, e.message)
        }
    }

    private fun handle(job: ComeCotasCheckpointJob): ProcessingOutcome {
        val holding =
            holdingRepository.findById(job.holdingId, job.accountId)
                ?: run {
                    logger.info {
                        "come-cotas checkpoint skipped missing holding jobId=${job.id} " +
                            "holdingId=${job.holdingId} checkpointDate=${job.checkpointDate}"
                    }
                    return ProcessingOutcome.Completed
                }
        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NotFoundException(CommonErrorCode.RESOURCE_NOT_FOUND, "Asset", holding.assetId)
        val fundCnpj =
            asset.fundCnpj?.filter(Char::isDigit)?.takeIf { it.length == 14 }
                ?: throw ValidationException(CommonErrorCode.INVALID_INPUT, field = "asset.fundCnpj")

        val snapshot = fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))[fundCnpj]
        val taxProfile = ComeCotasEligibility.resolveEffectiveTaxProfile(holding, asset, snapshot?.taxProfile)
        if (!ComeCotasEligibility.isEligible(holding, asset, taxProfile)) {
            logger.info {
                "come-cotas checkpoint skipped ineligible jobId=${job.id} holdingId=${holding.id} " +
                    "checkpointDate=${job.checkpointDate} fundCnpj=$fundCnpj " +
                    "providerTaxProfile=${snapshot?.taxProfile} " +
                    "effectiveTaxProfile=$taxProfile override=${holding.comeCotasEnabled}"
            }
            return ProcessingOutcome.Completed
        }

        val checkpointWindow = ComeCotasSupport.checkpointWindow(job.checkpointDate)
        if (
            investmentTransactionRepository.existsWithholdingTaxForHoldingInDateRange(
                holding.id!!,
                job.accountId,
                checkpointWindow.start,
                checkpointWindow.endInclusive,
            )
        ) {
            logger.info {
                "come-cotas checkpoint skipped existing withholding jobId=${job.id} holdingId=${holding.id} " +
                    "checkpointDate=${job.checkpointDate} windowStart=${checkpointWindow.start} " +
                    "windowEnd=${checkpointWindow.endInclusive}"
            }
            return ProcessingOutcome.Completed
        }

        val transactions =
            investmentTransactionRepository.findByHolding(holding.id, job.accountId)
                .sortedWith(
                    compareBy<InvestmentTransaction> { it.tradeDate }
                        .thenBy { it.createdAt }
                        .thenBy { it.id.toString() },
                )
        val actorUserId =
            transactions.maxWithOrNull(
                compareBy<InvestmentTransaction> { it.tradeDate }.thenBy { it.createdAt },
            )?.createdByUserId
                ?: throw ValidationException(CommonErrorCode.INVALID_INPUT, field = "holding.createdByUserId")

        return when (val computation = compute(holding, fundCnpj, taxProfile, transactions, job.checkpointDate)) {
            is ComputationResult.NoTaxDue -> {
                logger.info {
                    "come-cotas checkpoint no-tax-due jobId=${job.id} holdingId=${holding.id} " +
                        "checkpointDate=${job.checkpointDate} reason=${computation.reason}"
                }
                ProcessingOutcome.Completed
            }
            is ComputationResult.RetryLater ->
                ProcessingOutcome.Requeue(
                    errorCode = CommonErrorCode.SERVICE_UNAVAILABLE.code,
                    errorDetails = computation.reason,
                    delay = retryDelayFor(claimedAttemptCount = job.attemptCount),
                )
            is ComputationResult.Computed -> {
                logger.info {
                    "come-cotas checkpoint computed jobId=${job.id} holdingId=${holding.id} " +
                        "checkpointDate=${job.checkpointDate} irValue=${computation.irValue} " +
                        "quotasDeducted=${computation.quotasDeducted} nav=${computation.navAtCheckpoint} " +
                        "openLots=${computation.openLotCount} taxProfile=$taxProfile"
                }
                logger.info {
                    "come-cotas checkpoint confirming withholding jobId=${job.id} holdingId=${holding.id} " +
                        "checkpointDate=${job.checkpointDate}"
                }
                confirmWithholdingTaxService.confirm(
                    command =
                        ConfirmWithholdingTaxCommand(
                            holdingId = holding.id,
                            tradeDate = job.checkpointDate,
                            irValue = computation.irValue,
                            quotasDeducted = computation.quotasDeducted,
                            source = InvestmentSource.SYSTEM,
                            providerTransactionId = providerTransactionId(holding.id, job.checkpointDate),
                        ),
                    accountId = job.accountId,
                    userId = actorUserId,
                )
                logger.info {
                    "come-cotas checkpoint confirmed withholding jobId=${job.id} holdingId=${holding.id} " +
                        "checkpointDate=${job.checkpointDate}"
                }

                ProcessingOutcome.Completed
            }
        }
    }

    private fun compute(
        holding: Holding,
        fundCnpj: String,
        taxProfile: FundTaxProfile,
        transactions: List<InvestmentTransaction>,
        checkpointDate: LocalDate,
    ): ComputationResult {
        val transactionsUpToCheckpoint = transactions.filter { !it.tradeDate.isAfter(checkpointDate) }
        if (transactionsUpToCheckpoint.isEmpty()) {
            return ComputationResult.NoTaxDue("no-transactions-up-to-checkpoint")
        }

        val firstTradeDate = transactionsUpToCheckpoint.minOf { it.tradeDate }
        val navAtCheckpoint =
            fundMarketDataProvider.getNavSeries(fundCnpj, firstTradeDate, checkpointDate)
                .lastOrNull { !it.first.isAfter(checkpointDate) }
                ?: return ComputationResult.RetryLater("NAV unavailable for checkpoint $checkpointDate")

        val openLots = ComeCotasSupport.reconstructOpenLots(transactionsUpToCheckpoint)
        if (openLots.isEmpty()) {
            return ComputationResult.NoTaxDue("no-open-lots")
        }
        val baseQuantity = openLots.fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.quantity, mathContext) }

        val irValue =
            openLots.fold(BigDecimal.ZERO) { acc, lot ->
                val gross = lot.quantity.multiply(navAtCheckpoint.second, mathContext)
                val costBasis = lot.quantity.multiply(lot.unitPrice, mathContext)
                val gain = gross.subtract(costBasis, mathContext)
                if (gain <= BigDecimal.ZERO) {
                    acc
                } else {
                    val projected =
                        ComeCotasSupport.estimateProjectedComeCotas(
                            taxProfile = taxProfile,
                            gain = gain,
                            tradeDate = lot.tradeDate,
                            referenceDate = checkpointDate,
                        )
                    acc.add((projected - lot.withholdingPaid).max(BigDecimal.ZERO), mathContext)
                }
            }.setScale(2, RoundingMode.HALF_UP)

        if (irValue <= BigDecimal.ZERO) return ComputationResult.NoTaxDue("non-positive-ir-value")
        val quotasDeducted = irValue.divide(navAtCheckpoint.second, 8, RoundingMode.HALF_UP)
        if (quotasDeducted <= BigDecimal.ZERO) return ComputationResult.NoTaxDue("non-positive-quotas-deducted")
        logger.info {
            "come-cotas checkpoint diagnostics holdingId=${holding.id} checkpointDate=$checkpointDate " +
                "base_quantity=$baseQuantity nav_used=${navAtCheckpoint.second} " +
                "irValue=$irValue quotasDeducted=$quotasDeducted"
        }
        if (quotasDeducted > holding.quantity) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "quotasDeducted",
                parameters =
                    mapOf(
                        "holdingQuantity" to holding.quantity,
                        "quotasDeducted" to quotasDeducted,
                        "checkpointDate" to checkpointDate,
                    ),
            )
        }
        return ComputationResult.Computed(
            irValue = irValue,
            quotasDeducted = quotasDeducted,
            navAtCheckpoint = navAtCheckpoint.second,
            openLotCount = openLots.size,
        )
    }

    private fun complete(job: ComeCotasCheckpointJob) {
        jobRepository.update(
            job.copy(
                status = ComeCotasCheckpointJobStatus.COMPLETED,
                processingNodeId = null,
                lastHeartbeatAt = null,
                completedAt = Instant.now(),
                nextAttemptAt = null,
                errorCode = null,
                errorDetails = null,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun retryOrFail(
        job: ComeCotasCheckpointJob,
        errorCode: String,
        errorDetails: String?,
    ) {
        if (job.attemptCount >= maxAttempts) {
            fail(job, errorCode, errorDetails)
            return
        }
        requeue(job, errorCode, errorDetails, retryDelayFor(job.attemptCount))
    }

    private fun requeue(
        job: ComeCotasCheckpointJob,
        errorCode: String,
        errorDetails: String?,
        delay: Duration,
    ) {
        jobRepository.update(
            job.copy(
                status = ComeCotasCheckpointJobStatus.PENDING,
                processingNodeId = null,
                lastHeartbeatAt = null,
                nextAttemptAt = Instant.now().plus(delay),
                errorCode = errorCode,
                errorDetails = errorDetails,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun fail(
        job: ComeCotasCheckpointJob,
        errorCode: String,
        errorDetails: String?,
    ) {
        jobRepository.update(
            job.copy(
                status = ComeCotasCheckpointJobStatus.FAILED,
                processingNodeId = null,
                lastHeartbeatAt = null,
                nextAttemptAt = null,
                errorCode = errorCode,
                errorDetails = errorDetails,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun retryDelayFor(claimedAttemptCount: Int): Duration {
        val exponent = (claimedAttemptCount - 1).coerceAtLeast(0).coerceAtMost(6)
        return retryBaseDelay.multipliedBy(1L shl exponent)
    }

    private fun providerTransactionId(
        holdingId: UUID,
        checkpointDate: LocalDate,
    ): String = "comecotas:$holdingId:$checkpointDate"

    private sealed interface ProcessingOutcome {
        data object Completed : ProcessingOutcome

        data class Requeue(
            val errorCode: String,
            val errorDetails: String?,
            val delay: Duration,
        ) : ProcessingOutcome
    }

    private sealed interface ComputationResult {
        data class NoTaxDue(
            val reason: String,
        ) : ComputationResult

        data class RetryLater(
            val reason: String,
        ) : ComputationResult

        data class Computed(
            val irValue: BigDecimal,
            val quotasDeducted: BigDecimal,
            val navAtCheckpoint: BigDecimal,
            val openLotCount: Int,
        ) : ComputationResult
    }

    companion object {
        private val NODE_ID: String =
            runCatching { InetAddress.getLocalHost().hostName }.getOrElse { "unknown-node" }
    }
}
