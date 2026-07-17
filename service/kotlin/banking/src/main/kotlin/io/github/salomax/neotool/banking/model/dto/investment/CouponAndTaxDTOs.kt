package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ConfirmCouponPaymentCommand(
    val holdingId: UUID,
    val tradeDate: LocalDate,
    val grossValue: BigDecimal,
    val netValue: BigDecimal,
    val incomeTax: BigDecimal,
    val destinationAccountId: UUID,
    val pendingActionId: UUID? = null,
)

data class ConfirmCouponPaymentResult(
    val couponTransaction: InvestmentTransaction,
    val bankingTransaction: Transaction?,
    val pendingAction: AccountPendingAction?,
)

data class ConfirmWithholdingTaxCommand(
    val holdingId: UUID,
    val tradeDate: LocalDate,
    val irValue: BigDecimal,
    val quotasDeducted: BigDecimal,
    val source: InvestmentSource = InvestmentSource.MANUAL,
    val providerTransactionId: String? = null,
)

data class ConfirmWithholdingTaxResult(
    val withholdingTaxTransaction: InvestmentTransaction,
)
