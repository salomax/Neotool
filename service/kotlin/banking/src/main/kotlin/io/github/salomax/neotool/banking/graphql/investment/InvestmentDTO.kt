package io.github.salomax.neotool.banking.graphql.investment

import io.github.salomax.neotool.banking.domain.investment.AnbimaCategory
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.CouponFrequency
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.HoldingCloseReason
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.pendingaction.AccountPendingActionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import io.github.salomax.neotool.banking.service.investment.ValuationSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class HoldingPerformanceResolution {
    MINUTE,
    HOUR,
    DAY,
    MONTH,
}

enum class PerformancePeriodDTO {
    MTD,
    YTD,
    ONE_YEAR,
    THREE_YEARS,
    ALL,
}

enum class GridClassTwrPeriod {
    SIX_MONTHS,
    ONE_YEAR,
    THREE_YEARS,
    ALL,
}

// ── Asset ─────────────────────────────────────────────────────────────────────

data class InvestmentAssetDTO(
    val id: UUID,
    val ticker: String?,
    val isinCode: String?,
    val name: String,
    val assetType: AssetType,
    val exchange: String?,
    val currencyCode: String,
    val issuerCnpj: String?,
    val issuerName: String?,
    val fundCnpj: String?,
    val anbimaCategory: AnbimaCategory?,
    val anbimaClass: String?,
    val anbimaSubclass: String?,
    val logoAsset: Map<String, String>?,
    val priceFactor: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// ── Holding ───────────────────────────────────────────────────────────────────

data class InvestmentHoldingDTO(
    val id: UUID,
    val financialAccountId: UUID,
    val accountId: UUID,
    val assetId: UUID,
    val asset: InvestmentAssetDTO?,
    val financialAccount: FinancialAccountDTO?,
    val quantity: Double,
    val averageCost: Double,
    // RF-specific terms
    val indexer: Indexer?,
    val indexerPercentage: Double?,
    val fixedAnnualRate: Double?,
    val issueDate: LocalDate?,
    val maturityDate: LocalDate?,
    val gracePeriodDate: LocalDate?,
    val isTaxExempt: Boolean,
    val couponFrequency: CouponFrequency? = null,
    val couponDayOfMonth: Int? = null,
    val couponSchedule: List<CouponScheduleEntryDTO> = emptyList(),
    // Valuations
    val grossValue: Double?,
    val netValue: Double?,
    val incomeTax: Double?,
    val iof: Double?,
    /** Accrued interest for the current coupon period = grossValue − principal. Null when unavailable. */
    val accruedInterest: Double? = null,
    val lastValuedAt: Instant?,
    val pricingMode: HoldingPricingMode?,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
    val isExpired: Boolean,
    val isClosed: Boolean,
    val isExpiredPending: Boolean,
    val closedAt: LocalDate?,
    val closeReason: HoldingCloseReason?,
    // Provenance
    val source: InvestmentSource,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CouponScheduleEntryDTO(
    val seriesNumber: Int,
    val scheduledDate: LocalDate,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val isPartialPeriod: Boolean,
    val isPast: Boolean,
    val matchedTransaction: InvestmentTransactionDTO?,
)

// ── BrokerNote ────────────────────────────────────────────────────────────────

data class BrokerNoteDTO(
    val brokerNoteNumber: String?,
    val grossValue: Double,
    val brokerageFee: Double,
    val clearingSettlementFee: Double,
    val clearingRegistrationFee: Double,
    val stockExchangeAssetTradeNoticeFee: Double,
    val stockExchangeFee: Double,
    val clearingCustodyFee: Double,
    val incomeTax: Double,
    val taxes: Double,
    val netValue: Double,
    val createdAt: Instant,
)

// ── InvestmentTransaction ─────────────────────────────────────────────────────

data class InvestmentTransactionDTO(
    val id: UUID,
    val holdingId: UUID,
    val financialAccountId: UUID,
    val direction: InvestmentTransactionDirection,
    val type: InvestmentTransactionType,
    val typeAdditionalInfo: String?,
    val quantity: Double?,
    val unitPrice: Double?,
    val grossValue: Double,
    val netValue: Double?,
    val incomeTax: Double?,
    val iof: Double?,
    val indexerPercentage: Double?,
    val fixedAnnualRate: Double?,
    val priceFactor: Double?,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate?,
    val source: InvestmentSource,
    val brokerNote: BrokerNoteDTO?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ConfirmCouponPaymentResultDTO(
    val couponTransaction: InvestmentTransactionDTO,
    val bankingTransaction: TransactionDTO?,
    val pendingAction: AccountPendingActionDTO?,
)

data class ConfirmWithholdingTaxResultDTO(
    val withholdingTaxTransaction: InvestmentTransactionDTO,
)

data class HoldingPerformanceCapabilitiesDTO(
    val supportsProjection: Boolean,
    val projectionDefaultEnabled: Boolean,
    val supportedResolutions: List<HoldingPerformanceResolution>,
    val defaultResolution: HoldingPerformanceResolution,
)

data class HoldingPerformanceSummaryDTO(
    val investedPrincipal: Double?,
    val netContributions: Double?,
    val currentCostBasis: Double?,
    val grossValue: Double?,
    val netValue: Double?,
    val incomeTax: Double?,
    val iof: Double?,
    val grossGain: Double?,
    val netGain: Double?,
    val grossGainPercent: Double?,
)

data class HoldingPerformancePointDTO(
    val timestamp: Instant,
    val isProjected: Boolean,
    val investedPrincipal: Double?,
    val netContributions: Double?,
    val currentCostBasis: Double?,
    val grossValue: Double?,
    val netValue: Double?,
    val incomeTax: Double?,
    val iof: Double?,
    val grossGain: Double?,
    val netGain: Double?,
    val quantity: Double?,
)

data class HoldingPerformanceDTO(
    val holdingId: UUID,
    val assetType: AssetType,
    val currencyCode: String,
    val resolution: HoldingPerformanceResolution,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
    val capabilities: HoldingPerformanceCapabilitiesDTO,
    val summary: HoldingPerformanceSummaryDTO,
    val points: List<HoldingPerformancePointDTO>,
)

data class PortfolioPerformanceSeriesDTO(
    val groupKey: String?,
    val label: String,
    val currencyCode: String,
    val resolution: HoldingPerformanceResolution,
    val valuationSource: ValuationSource,
    val valuationReferenceDate: LocalDate?,
    val capabilities: HoldingPerformanceCapabilitiesDTO,
    val summary: HoldingPerformanceSummaryDTO,
    val points: List<HoldingPerformancePointDTO>,
    val excludedHoldingsCount: Int,
    val includedHoldingsCount: Int,
)

data class InvestmentHoldingPerformanceDTO(
    val holdingId: UUID,
    val xirr: Double?,
    val xirrSince: LocalDate?,
    val totalReturn: Double,
    val totalReturnAbsolute: Double,
    val dividendsReceived: Double,
)

data class BenchmarkPerformanceDTO(
    val code: String,
    val label: String,
    val twr: Double?,
)

data class GridClassTwrPointDTO(
    val date: LocalDate,
    val quotaValue: Double,
)

data class PortfolioPerformanceDTO(
    val twr: Double?,
    val twrSince: LocalDate?,
    val twrAbsolute: Double?,
    val period: PerformancePeriodDTO,
    val benchmarks: List<BenchmarkPerformanceDTO>,
    val hasEstimatedValues: Boolean,
)

data class RecalculationStatusDTO(
    val isCalculating: Boolean,
    val jobId: UUID?,
    val startedAt: Instant?,
    /** Stable code for `errors.*` i18n keys; prefer over [message] for user-facing copy. */
    val errorCode: String? = null,
    val message: String?,
    /** Root-cause detail for support/debugging (exception class + message). Not intended for end-user display. */
    val errorDetails: String? = null,
)

data class AssetGroupSummaryDTO(
    val groupKey: String,
    val label: String,
    val count: Int,
    val totalCostBasis: Double,
    val totalGrossValue: Double,
    val grossGainPercent: Double?,
    val twr: Double?,
    val allocationPercent: Double,
    val isCash: Boolean,
)

data class InvestmentPortfolioSummaryDTO(
    val totalHoldingsCount: Int,
    val totalCostBasis: Double,
    val totalGrossValue: Double,
    val totalNetValue: Double,
    val totalIncomeTax: Double,
    val totalIof: Double,
    val grossGain: Double,
    val grossGainPercent: Double,
    val netGain: Double,
    val netGainPercent: Double,
    val cashBalance: Double,
    val totalPortfolioValue: Double,
    val byAssetGroup: List<AssetGroupSummaryDTO>,
    val hasEstimatedValues: Boolean,
    val valuationReferenceDate: LocalDate?,
    val currencyCode: String,
    val performance: PortfolioPerformanceDTO,
    val recalculationStatus: RecalculationStatusDTO,
)
