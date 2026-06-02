package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class RecordStockLendingCommand(
    val holdingId: UUID,
    val tradeDate: LocalDate,
    val grossValue: BigDecimal,
    val netValue: BigDecimal,
    val incomeTax: BigDecimal,
    val destinationAccountId: UUID?,
)

data class RecordStockLendingResult(
    val lendingTransaction: InvestmentTransaction,
    val bankingTransaction: Transaction?,
)
