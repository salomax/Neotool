package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationJob
import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationStatus
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.TaxRecalculationJobRepository
import io.github.salomax.neotool.banking.service.investment.tax.TaxRegimeRegistry
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class TaxRecalculationService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val taxRecalculationJobRepository: TaxRecalculationJobRepository,
) {
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

    internal fun hasPeriodicWithholding(
        holding: Holding,
        asset: Asset,
    ): Boolean {
        if (asset.assetType != io.github.salomax.neotool.banking.domain.investment.AssetType.MUTUAL_FUND) return false
        val taxProfile = ComeCotasEligibility.resolveEffectiveTaxProfile(holding, asset, snapshotTaxProfile = null)
        return TaxRegimeRegistry.resolveFund(
            assetType = asset.assetType,
            taxProfile = taxProfile,
            comeCotasEnabled = holding.comeCotasEnabled,
        ).hasPeriodicWithholding
    }

    private companion object {
        const val TRIGGERED_BY_MAX_LENGTH = 100
    }
}
