package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.domain.investment.BrokerNote
import io.github.salomax.neotool.banking.domain.investment.CouponFrequency
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Command to record a new investment transaction.
 */
data class RecordInvestmentTransactionCommand(
    val financialAccountId: UUID,
    val assetId: UUID,
    val direction: InvestmentTransactionDirection,
    val type: InvestmentTransactionType,
    val typeAdditionalInfo: String? = null,
    val quantity: BigDecimal? = null,
    val unitPrice: BigDecimal? = null,
    val grossValue: BigDecimal,
    val netValue: BigDecimal? = null,
    val incomeTax: BigDecimal? = null,
    val iof: BigDecimal? = null,
    val indexerPercentage: BigDecimal? = null,
    val fixedAnnualRate: BigDecimal? = null,
    val priceFactor: BigDecimal? = null,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate? = null,
    val source: InvestmentSource = InvestmentSource.MANUAL,
    val providerTransactionId: String? = null,
    val importJobId: UUID? = null,
    val cashDestinationAccountId: UUID? = null,
    val brokerNote: BrokerNote? = null,
    val indexer: Indexer? = null,
    val issueDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val gracePeriodDate: LocalDate? = null,
    val isTaxExempt: Boolean = false,
    val couponFrequency: CouponFrequency? = null,
    /** 1–31 when private credit pays on a fixed calendar day; null for Tesouro and AT_MATURITY. */
    val couponDayOfMonth: Int? = null,
    val providerInvestmentId: String? = null,
    val pricingMode: HoldingPricingMode? = null,
    val manualValuation: ManualFundValuation? = null,
)

data class ManualFundValuation(
    val grossValue: BigDecimal,
    val valuedAt: LocalDate,
)

/**
 * Command to update an existing MANUAL investment transaction.
 */
data class UpdateInvestmentTransactionCommand(
    val direction: InvestmentTransactionDirection,
    val type: InvestmentTransactionType,
    val typeAdditionalInfo: String? = null,
    val quantity: BigDecimal? = null,
    val unitPrice: BigDecimal? = null,
    val grossValue: BigDecimal,
    val netValue: BigDecimal? = null,
    val incomeTax: BigDecimal? = null,
    val iof: BigDecimal? = null,
    val indexerPercentage: BigDecimal? = null,
    val fixedAnnualRate: BigDecimal? = null,
    val priceFactor: BigDecimal? = null,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate? = null,
    val brokerNote: BrokerNote? = null,
    val indexer: Indexer? = null,
    val issueDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val gracePeriodDate: LocalDate? = null,
    val isTaxExempt: Boolean? = null,
    val couponFrequency: CouponFrequency? = null,
    val couponDayOfMonth: Int? = null,
    val pricingMode: HoldingPricingMode? = null,
    val manualValuation: ManualFundValuation? = null,
)
