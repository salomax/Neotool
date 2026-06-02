package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.model.dto.investment.PortfolioRecalculationRequestedEvent
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class PortfolioFlowEventService(
    private val portfolioFlowEventRepository: PortfolioFlowEventRepository,
    private val portfolioQuotaRepository: PortfolioQuotaRepository,
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository,
    private val recalculationRequestedEventPublisher: ApplicationEventPublisher<PortfolioRecalculationRequestedEvent>,
) {
    @Transactional
    open fun recordEvent(
        financialAccountId: UUID,
        accountId: UUID,
        eventDate: LocalDate,
        eventType: PortfolioFlowEventType,
        grossAmount: BigDecimal,
        relatedHoldingId: UUID? = null,
        sourceReferenceId: UUID? = null,
        sourceReferenceType: String? = null,
    ): InvestmentPortfolioFlowEvent {
        require(grossAmount.signum() >= 0) { "grossAmount must be >= 0" }

        if (sourceReferenceId != null && sourceReferenceType != null) {
            portfolioFlowEventRepository.deleteBySourceReference(sourceReferenceId, sourceReferenceType)
        }

        val latestQuota =
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = eventDate,
            )
        val signedAmount = signedAmount(eventType, grossAmount)
        val portfolioValueBefore = latestQuota?.totalPortfolioValue?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
        val quotaValueAtEvent = latestQuota?.quotaValue ?: INITIAL_QUOTA_VALUE
        val quotasTransacted =
            if (quotaValueAtEvent.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                signedAmount.divide(quotaValueAtEvent, QUOTA_SCALE, RoundingMode.HALF_UP)
            }

        val savedEvent =
            portfolioFlowEventRepository.save(
                InvestmentPortfolioFlowEvent(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    eventDate = eventDate,
                    eventType = eventType,
                    grossAmount = grossAmount,
                    portfolioValueBefore = portfolioValueBefore.max(BigDecimal.ZERO),
                    quotaValueAtEvent = quotaValueAtEvent,
                    quotasTransacted = quotasTransacted,
                    relatedHoldingId = relatedHoldingId,
                    sourceReferenceId = sourceReferenceId,
                    sourceReferenceType = sourceReferenceType,
                ),
            )

        requestRecalculation(
            financialAccountId = financialAccountId,
            accountId = accountId,
            recalculateFrom = eventDate,
            triggeredBy = buildTriggeredBy(sourceReferenceType ?: eventType.name.lowercase(), sourceReferenceId),
        )

        return savedEvent
    }

    @Transactional
    open fun requestRecalculation(
        financialAccountId: UUID?,
        accountId: UUID,
        recalculateFrom: LocalDate,
        triggeredBy: String,
    ): PortfolioRecalculationJob {
        val now = Instant.now()
        // If a PENDING job already exists for this account, merge into it (take earliest recalculateFrom).
        val existing = portfolioRecalculationJobRepository.findPendingByAccount(accountId)

        val job =
            if (existing != null) {
                portfolioRecalculationJobRepository.update(
                    existing.copy(
                        financialAccountId = financialAccountId ?: existing.financialAccountId,
                        recalculateFrom = minOf(existing.recalculateFrom, recalculateFrom),
                        triggeredBy = triggeredBy.take(TRIGGERED_BY_MAX_LENGTH),
                        updatedAt = now,
                    ),
                )
            } else {
                // No PENDING job exists. If a PROCESSING job is already in-flight it may be
                // rewriting quotas starting from an earlier date than the new event's tradeDate.
                // After that job completes, the new PENDING job must re-run at least from the
                // same start so that any quotas deleted by the in-flight job are rebuilt with
                // the latest flow-event state.  Using only the new event's date would create a
                // gap where the in-flight job's (now stale) quota writes aren't overwritten.
                val processing = portfolioRecalculationJobRepository.findProcessingByAccount(accountId)
                val effectiveFrom =
                    if (processing != null) {
                        minOf(recalculateFrom, processing.recalculateFrom)
                    } else {
                        recalculateFrom
                    }

                portfolioRecalculationJobRepository.save(
                    PortfolioRecalculationJob(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        status = PortfolioRecalculationStatus.PENDING,
                        recalculateFrom = effectiveFrom,
                        triggeredBy = triggeredBy.take(TRIGGERED_BY_MAX_LENGTH),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

        recalculationRequestedEventPublisher.publishEvent(
            PortfolioRecalculationRequestedEvent(
                jobId = requireNotNull(job.id),
                accountId = job.accountId,
                financialAccountId = job.financialAccountId,
                recalculateFrom = job.recalculateFrom,
                triggeredBy = job.triggeredBy,
                requestedAt = now,
            ),
        )

        return job
    }

    @Transactional
    open fun deleteEventBySourceReference(
        sourceReferenceId: UUID,
        sourceReferenceType: String,
    ): Int = portfolioFlowEventRepository.deleteBySourceReference(sourceReferenceId, sourceReferenceType)

    /** Removes any flow rows still keyed by [relatedHoldingId] (safety net after per-transaction deletes). */
    @Transactional
    open fun deleteEventsByRelatedHolding(
        financialAccountId: UUID,
        relatedHoldingId: UUID,
    ): Int =
        portfolioFlowEventRepository.deleteByFinancialAccountAndRelatedHoldingId(financialAccountId, relatedHoldingId)

    private fun signedAmount(
        eventType: PortfolioFlowEventType,
        grossAmount: BigDecimal,
    ): BigDecimal =
        when (eventType) {
            PortfolioFlowEventType.DEPOSIT -> grossAmount
            PortfolioFlowEventType.WITHDRAWAL, PortfolioFlowEventType.INCOME_TAX -> grossAmount.negate()
            PortfolioFlowEventType.DIVIDEND -> grossAmount.negate()
            PortfolioFlowEventType.CORPORATE_ACTION -> BigDecimal.ZERO
        }

    private fun buildTriggeredBy(
        prefix: String,
        sourceReferenceId: UUID?,
    ): String = sourceReferenceId?.let { "$prefix:$it" } ?: prefix

    private companion object {
        val INITIAL_QUOTA_VALUE: BigDecimal = BigDecimal.ONE.setScale(8, RoundingMode.HALF_UP)
        const val QUOTA_SCALE = 8
        const val TRIGGERED_BY_MAX_LENGTH = 100
    }
}
