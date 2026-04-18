package io.github.salomax.neotool.banking.service.investment.lifecycle

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import java.util.UUID

data class InvestmentTransactionContext(
    val transaction: InvestmentTransaction?,
    val holding: Holding,
    val asset: Asset,
    val command: RecordInvestmentTransactionCommand?,
    val accountId: UUID,
    val userId: UUID,
)
