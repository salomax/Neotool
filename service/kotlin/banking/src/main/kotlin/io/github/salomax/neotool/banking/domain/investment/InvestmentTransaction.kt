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
    val financialAccountId: UUID,         // Denormalized for fast tenant-level queries
    val accountId: UUID,
    val createdByUserId: UUID,

    // ── Classification ────────────────────────────────────────────────────────
    val direction: InvestmentTransactionDirection,
    val type: InvestmentTransactionType,
    val typeAdditionalInfo: String? = null, // Required when type = OTHER

    // ── Amounts ───────────────────────────────────────────────────────────────
    val quantity: BigDecimal? = null,       // Null for DIVIDEND, INTEREST_PAYMENT, etc.
    val unitPrice: BigDecimal? = null,
    val grossValue: BigDecimal,             // Always required and positive
    val netValue: BigDecimal? = null,       // grossValue − incomeTax − iof

    // ── Brazilian taxes retained at source ────────────────────────────────────
    val incomeTax: BigDecimal? = null,      // IR retido na operação
    val iof: BigDecimal? = null,            // IOF retido (RF < 30 dias)

    // ── Fixed income context ──────────────────────────────────────────────────
    val indexerPercentage: BigDecimal? = null, // Rate applied in this transaction
    val agreedRate: BigDecimal? = null,        // Pre-fixed rate (OFB: remunerationTransactionRate)

    // ── Equity context ────────────────────────────────────────────────────────
    val priceFactor: BigDecimal? = null,       // B3 price factor for this transaction

    // ── Dates ─────────────────────────────────────────────────────────────────
    val tradeDate: LocalDate,               // Fato gerador — used for IR month attribution
    val settlementDate: LocalDate? = null,  // D+2 equities, D+1 RF

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
    val taxes: BigDecimal = BigDecimal.ZERO,  // ISS, emolumentos, outros
    val netValue: BigDecimal,
    val createdAt: Instant = Instant.now(),
)
