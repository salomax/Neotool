package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Command to record a new investment transaction.
 *
 * The service resolves the [Holding] from ([financialAccountId], [assetId]) — creating it
 * on the first PURCHASE — so the caller never supplies a holdingId directly.
 *
 * RF-specific holding terms ([indexer], [preFixedRate], [maturityDate], etc.) are only
 * used when creating a brand-new holding (first PURCHASE of a fixed income instrument).
 * They are ignored on subsequent transactions against an existing holding.
 */
data class RecordInvestmentTransactionCommand(
    val financialAccountId: UUID,
    val assetId: UUID,

    // ── Classification ────────────────────────────────────────────────────────
    val direction: InvestmentTransactionDirection,
    val type: InvestmentTransactionType,
    val typeAdditionalInfo: String? = null,

    // ── Amounts ───────────────────────────────────────────────────────────────
    val quantity: BigDecimal? = null,
    val unitPrice: BigDecimal? = null,
    val grossValue: BigDecimal,
    val netValue: BigDecimal? = null,

    // ── Brazilian taxes ───────────────────────────────────────────────────────
    val incomeTax: BigDecimal? = null,
    val iof: BigDecimal? = null,

    // ── Fixed income context ──────────────────────────────────────────────────
    val indexerPercentage: BigDecimal? = null,
    val agreedRate: BigDecimal? = null,

    // ── Equity context ────────────────────────────────────────────────────────
    val priceFactor: BigDecimal? = null,

    // ── Dates ─────────────────────────────────────────────────────────────────
    val tradeDate: LocalDate,
    val settlementDate: LocalDate? = null,

    // ── Provenance ────────────────────────────────────────────────────────────
    val source: InvestmentSource = InvestmentSource.MANUAL,
    val providerTransactionId: String? = null,
    val importJobId: UUID? = null,

    // ── Broker note (renda variável only) ─────────────────────────────────────
    val brokerNote: BrokerNote? = null,

    // ── New holding terms (RF — only applied when holding does not yet exist) ─
    val indexer: Indexer? = null,
    val preFixedRate: BigDecimal? = null,
    val issueDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val gracePeriodDate: LocalDate? = null,
    val isTaxExempt: Boolean = false,
    val providerInvestmentId: String? = null,
    val pricingMode: HoldingPricingMode? = null,
    val manualValuation: ManualFundValuation? = null,
)

data class ManualFundValuation(
    val grossValue: BigDecimal,
    val valuedAt: LocalDate,
)
