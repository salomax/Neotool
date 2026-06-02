package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * An event that changes (or explains) an investment holding.
 *
 * [tradeDate] is the legally relevant date (fato gerador for IR calculation).
 * [settlementDate] is when cash and securities actually settle (D+2 equities, D+1 RF).
 *
 * [grossValue] is always positive; direction is carried by [direction].
 * [quantity] is null for pure monetary events (DIVIDEND, INTEREST_PAYMENT).
 *
 * [typeAdditionalInfo] is required when [type] = [InvestmentTransactionType.OTHER],
 * mirroring OFB transactionTypeAdditionalInfo.
 */
data class InvestmentTransaction(
    val id: UUID? = null,
    // Ownership
    val holdingId: UUID,
    // Denormalized for fast tenant-level queries
    val financialAccountId: UUID,
    val accountId: UUID,
    val createdByUserId: UUID,
    // ── Classification ────────────────────────────────────────────────────────
    val direction: InvestmentTransactionDirection,
    val type: InvestmentTransactionType,
    // Required when type = OTHER
    val typeAdditionalInfo: String? = null,
    // ── Amounts ───────────────────────────────────────────────────────────────
    // Null for DIVIDEND, INTEREST_PAYMENT, etc.
    val quantity: BigDecimal? = null,
    val unitPrice: BigDecimal? = null,
    // Always required and positive
    val grossValue: BigDecimal,
    // grossValue − incomeTax − iof
    val netValue: BigDecimal? = null,
    // ── Brazilian taxes retained at source ────────────────────────────────────
    // IR retido na operação
    val incomeTax: BigDecimal? = null,
    // IOF retido (RF < 30 dias)
    val iof: BigDecimal? = null,
    // ── Fixed income context ──────────────────────────────────────────────────
    // Rate applied in this transaction
    val indexerPercentage: BigDecimal? = null,
    // Canonical fixed leg rate (OFB: remunerationTransactionRate)
    val fixedAnnualRate: BigDecimal? = null,
    // ── Equity context ────────────────────────────────────────────────────────
    // B3 price factor for this transaction
    val priceFactor: BigDecimal? = null,
    // ── Dates ─────────────────────────────────────────────────────────────────
    // Fato gerador — used for IR month attribution
    val tradeDate: LocalDate,
    // D+2 equities, D+1 RF
    val settlementDate: LocalDate? = null,
    // ── Provenance ────────────────────────────────────────────────────────────
    val source: InvestmentSource = InvestmentSource.MANUAL,
    val providerTransactionId: String? = null,
    val importJobId: UUID? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    // 1:1 broker note (renda variável only)
    val brokerNote: BrokerNote? = null,
)

/**
 * Fee breakdown from a broker note (nota de corretagem).
 * 1:1 with [InvestmentTransaction]. Present only for renda variável transactions.
 * All fee fields default to zero — present but zero when not charged.
 *
 * Field names aligned with OFB brokerNote spec and Pluggy Expenses object.
 */
data class BrokerNote(
    val investmentTransactionId: UUID? = null,
    val accountId: UUID,
    val brokerNoteNumber: String? = null,
    val grossValue: BigDecimal,
    val brokerageFee: BigDecimal = BigDecimal.ZERO,
    val clearingSettlementFee: BigDecimal = BigDecimal.ZERO,
    val clearingRegistrationFee: BigDecimal = BigDecimal.ZERO,
    val stockExchangeAssetTradeNoticeFee: BigDecimal = BigDecimal.ZERO,
    val stockExchangeFee: BigDecimal = BigDecimal.ZERO,
    val clearingCustodyFee: BigDecimal = BigDecimal.ZERO,
    val incomeTax: BigDecimal = BigDecimal.ZERO,
    // ISS, emolumentos, outros
    val taxes: BigDecimal = BigDecimal.ZERO,
    val netValue: BigDecimal,
    val createdAt: Instant = Instant.now(),
)
