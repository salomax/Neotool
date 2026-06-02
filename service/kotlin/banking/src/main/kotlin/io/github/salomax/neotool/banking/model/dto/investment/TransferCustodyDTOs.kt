package io.github.salomax.neotool.banking.model.dto.investment

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class TransferCustodyCommand(
    val sourceHoldingId: UUID,
    val destinationFinancialAccountId: UUID,
    val quantity: BigDecimal?,
    val tradeDate: LocalDate,
)

data class TransferCustodyResult(
    val outflowTransaction: InvestmentTransaction,
    val inflowTransaction: InvestmentTransaction,
)
