package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.HoldingCloseReason
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class MaturityCashMovementCommand(
    val financialAccountId: UUID,
    val amount: BigDecimal,
    val date: LocalDate,
)

data class HoldingMaturityConfirmCommand(
    val holdingId: UUID,
    val maturityDate: LocalDate,
    val grossValue: BigDecimal,
    val netValue: BigDecimal,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val closeReason: HoldingCloseReason,
    val cashMovement: MaturityCashMovementCommand?,
)

data class HoldingMaturityConfirmResult(
    val maturityTransaction: InvestmentTransaction,
    val bankingTransaction: Transaction?,
    val pendingAction: AccountPendingAction,
)
