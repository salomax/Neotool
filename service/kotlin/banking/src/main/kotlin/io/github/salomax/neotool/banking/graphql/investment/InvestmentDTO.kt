package io.github.salomax.neotool.banking.graphql.investment

import io.github.salomax.neotool.banking.domain.investment.AnbimaCategory
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.HoldingCloseReason
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountDTO
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
    // Valuations
    val grossValue: Double?,
    val netValue: Double?,
    val incomeTax: Double?,
    val iof: Double?,
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
