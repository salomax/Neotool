package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A user's current position in an asset within a custodian account.
 *
 * One holding per (financialAccountId, assetId). [quantity] aggregates all lots.
 * [averageCost] is maintained by InvestmentTransactionService — never set directly.
 *
 * Current market value is computed at query time (quantity × latest price from
 * financial-data service). [grossValue] / [netValue] / [incomeTax] / [iof] are
 * provider-cached values populated on Open Finance sync and may be stale.
 *
 * Fixed income position-specific terms ([indexer], [maturity date], etc.) live here
 * because two holdings of the same [AssetType] can have different contractual terms.
 */
data class Holding(
    val id: UUID? = null,

    // Ownership
    val financialAccountId: UUID,         // The custodian account (type = INVESTMENT)
    val accountId: UUID,

    // Asset reference
    val assetId: UUID,

    // ── Position ──────────────────────────────────────────────────────────────
    // quantity >= 0. When 0, the position is closed (row is kept for history).
    val quantity: BigDecimal,

    // averageCost: weighted average purchase price per unit (custo médio ponderado).
    // Recalculated by the service on every PURCHASE and SALE.
    val averageCost: BigDecimal = BigDecimal.ZERO,

    // ── Fixed income position-specific terms ──────────────────────────────────
    // Null for equities, FIIs, ETFs, BDRs.
    val indexer: Indexer? = null,
    val indexerPercentage: BigDecimal? = null,  // 1.15 = 115% CDI
    val fixedAnnualRate: BigDecimal? = null,    // Fixed leg / spread / pre-fixed total, depending on indexer
    val issueDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val gracePeriodDate: LocalDate? = null,
    val isTaxExempt: Boolean = false,           // LCI, LCA, CRI, CRA are exempt for PF

    // ── Provider-cached valuations ────────────────────────────────────────────
    // Updated on Open Finance sync. Null until first sync.
    val grossValue: BigDecimal? = null,          // quantity × current gross unit price
    val netValue: BigDecimal? = null,            // grossValue − incomeTax − iof
    val incomeTax: BigDecimal? = null,           // IR provisionado
    val iof: BigDecimal? = null,                 // IOF provisionado (redemptions < 30 days)
    val lastValuedAt: Instant? = null,

    // ── Manual fund valuation ────────────────────────────────────────────────
    val pricingMode: HoldingPricingMode? = null,
    val manualGrossValue: BigDecimal? = null,
    val manualValuedAt: LocalDate? = null,

    // ── Provenance ────────────────────────────────────────────────────────────
    val source: InvestmentSource = InvestmentSource.MANUAL,
    val providerInvestmentId: String? = null,   // OFB investmentId / Pluggy id / Belvo id

    val deletedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
