package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxCommand
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxResult
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.util.UUID

@Singleton
open class ConfirmWithholdingTaxService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentTransactionService: InvestmentTransactionService,
) {
    @Transactional
    open fun reconcile(
        command: ConfirmWithholdingTaxCommand,
        accountId: UUID,
        userId: UUID,
    ): ConfirmWithholdingTaxResult? {
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

        val withholdingTaxTransaction =
            investmentTransactionService.reconcileSystemWithholdingTax(
                holding = holding,
                command = command,
                accountId = accountId,
                userId = userId,
            ) ?: return null

        return ConfirmWithholdingTaxResult(withholdingTaxTransaction = withholdingTaxTransaction)
    }

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
        return requireNotNull(reconcile(command, accountId, userId))
    }
}
