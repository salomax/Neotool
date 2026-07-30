package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.model.dto.investment.FundValuationResult
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointRepository
import io.github.salomax.neotool.banking.service.investment.tax.TaxRegimeRegistry
import io.github.salomax.neotool.common.finance.tax.engine.TaxLot
import io.github.salomax.neotool.common.finance.tax.engine.computeTax
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Singleton
class FundValuationService(
    private val checkpointRepository: ComeCotasCheckpointRepository = NoOpComeCotasCheckpointRepository,
) {
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun estimate(
        holding: Holding,
        snapshot: FundMarketSnapshot,
        transactions: List<InvestmentTransaction>,
    ): FundValuationResult {
        val nav = snapshot.latestNav ?: return unavailable()
        val grossValue = holding.quantity.multiply(nav.closePrice, mathContext)

        return when (snapshot.taxProfile) {
            FundTaxProfile.UNKNOWN,
            FundTaxProfile.PENSION,
            ->
                FundValuationResult(
                    grossValue = grossValue,
                    incomeTax = null,
                    iof = null,
                    netValue = null,
                    valuationSource = ValuationSource.MARKET_DATA,
                    valuationReferenceDate = nav.date,
                )

            else -> {
                val openLots = reconstructOpenLots(transactions)
                if (openLots.isEmpty()) {
                    return FundValuationResult(
                        grossValue = grossValue,
                        incomeTax = null,
                        iof = null,
                        netValue = grossValue,
                        valuationSource = ValuationSource.MARKET_DATA,
                        valuationReferenceDate = nav.date,
                    )
                }

                val regime =
                    TaxRegimeRegistry.resolveFund(
                        assetType = io.github.salomax.neotool.banking.domain.investment.AssetType.MUTUAL_FUND,
                        taxProfile = snapshot.taxProfile,
                        comeCotasEnabled = holding.comeCotasEnabled,
                    )
                val computation =
                    computeTax(
                        lots =
                            openLots.mapIndexed { index, lot ->
                                TaxLot(
                                    id = "${holding.id ?: "holding"}-$index-${lot.tradeDate}",
                                    quantity = lot.quantity,
                                    unitCost = lot.unitPrice,
                                    acquisitionDate = lot.tradeDate,
                                )
                            },
                        regime = regime,
                        referenceDate = nav.date,
                    ) { date ->
                        if (date == nav.date) nav.closePrice else null
                    }
                val totalPreviouslyWithheld = previouslyWithheldAmount(holding.id, openLots, nav.date)
                val outstandingPeriodicWithholding =
                    computation.totalPeriodicWithholdingDue
                        .subtract(totalPreviouslyWithheld, mathContext)
                        .max(BigDecimal.ZERO)
                val finalResidualTax =
                    computation.totalFinalTaxDue
                        .subtract(computation.totalPeriodicWithholdingDue, mathContext)
                        .max(BigDecimal.ZERO)
                val totalIof = computation.totalIof
                val incomeTax = outstandingPeriodicWithholding.add(finalResidualTax, mathContext)
                val netValue = grossValue.subtract(totalIof, mathContext).subtract(incomeTax, mathContext)

                FundValuationResult(
                    grossValue = grossValue,
                    incomeTax = incomeTax,
                    iof = totalIof,
                    netValue = netValue,
                    valuationSource = ValuationSource.MARKET_DATA,
                    valuationReferenceDate = nav.date,
                )
            }
        }
    }

    private fun reconstructOpenLots(transactions: List<InvestmentTransaction>): List<OpenLot> =
        ComeCotasSupport.reconstructOpenLots(transactions)

    private fun previouslyWithheldAmount(
        holdingId: UUID?,
        openLots: List<OpenLot>,
        referenceDate: LocalDate,
    ): BigDecimal {
        val firstTradeDate = openLots.minOfOrNull(OpenLot::tradeDate) ?: return BigDecimal.ZERO
        val persistedWithheld =
            holdingId
                ?.let {
                    checkpointRepository
                        .findByHoldingAndDateRange(it, firstTradeDate, referenceDate)
                        .fold(BigDecimal.ZERO) { acc, checkpoint ->
                            acc.add(checkpoint.withheldAmount, mathContext)
                        }
                }
                ?: BigDecimal.ZERO
        return persistedWithheld.setScale(2, RoundingMode.HALF_UP)
    }

    private fun unavailable(): FundValuationResult =
        FundValuationResult(
            grossValue = null,
            incomeTax = null,
            iof = null,
            netValue = null,
            valuationSource = ValuationSource.UNAVAILABLE,
            valuationReferenceDate = null,
        )

    private object NoOpComeCotasCheckpointRepository : ComeCotasCheckpointRepository {
        override fun upsert(checkpoint: ComeCotasCheckpoint): ComeCotasCheckpoint = checkpoint

        override fun findByHoldingAndDateRange(
            holdingId: UUID,
            from: LocalDate,
            to: LocalDate,
        ): List<ComeCotasCheckpoint> = emptyList()
    }
}
