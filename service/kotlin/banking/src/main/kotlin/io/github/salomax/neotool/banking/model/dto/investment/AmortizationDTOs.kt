package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class RecordAmortizationCommand(
    val holdingId: UUID,
    val tradeDate: LocalDate,
    val grossValue: BigDecimal,
    val netValue: BigDecimal,
    val incomeTax: BigDecimal,
    val iof: BigDecimal = BigDecimal.ZERO,
    val destinationAccountId: UUID?,
)

data class RecordAmortizationResult(
    val amortizationTransaction: InvestmentTransaction,
    val bankingTransaction: Transaction?,
)
