package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxCommand
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxResult
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Singleton
open class ConfirmWithholdingTaxService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentTransactionService: InvestmentTransactionService,
) {
    @Transactional
    open fun confirm(
        command: ConfirmWithholdingTaxCommand,
        accountId: UUID,
        userId: UUID,
    ): ConfirmWithholdingTaxResult {
        val holding =
            holdingRepository.findById(command.holdingId, accountId)
                ?: throw NoSuchElementException("Holding ${command.holdingId} not found")
        requireNotNull(holding.id) { "Holding id must not be null" }

        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NoSuchElementException("Asset ${holding.assetId} not found")

        require(asset.assetType == AssetType.MUTUAL_FUND) {
            "WITHHOLDING_TAX is only valid for MUTUAL_FUND holdings"
        }
        require(command.irValue > BigDecimal.ZERO) { "irValue must be > 0" }
        require(command.quotasDeducted > BigDecimal.ZERO) { "quotasDeducted must be > 0" }
        require(holding.quantity >= command.quotasDeducted) {
            "Come-cotas quantity (${command.quotasDeducted}) exceeds current holding quantity (${holding.quantity})"
        }

        val unitPrice = command.irValue.divide(command.quotasDeducted, UNIT_PRICE_SCALE, RoundingMode.HALF_UP)

        val withholdingTaxTransaction =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = holding.financialAccountId,
                        assetId = holding.assetId,
                        direction = InvestmentTransactionDirection.OUTFLOW,
                        type = InvestmentTransactionType.WITHHOLDING_TAX,
                        grossValue = command.irValue,
                        netValue = command.irValue,
                        quantity = command.quotasDeducted,
                        unitPrice = unitPrice,
                        tradeDate = command.tradeDate,
                        settlementDate = command.tradeDate,
                        source = command.source,
                        providerInvestmentId = holding.providerInvestmentId,
                        providerTransactionId = command.providerTransactionId,
                    ),
                accountId = accountId,
                userId = userId,
            )

        return ConfirmWithholdingTaxResult(withholdingTaxTransaction = withholdingTaxTransaction)
    }

    companion object {
        private const val UNIT_PRICE_SCALE: Int = 8
    }
}
