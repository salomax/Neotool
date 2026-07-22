package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.model.dto.investment.UpdateFundHoldingParamsCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class InvestmentHoldingOverrideService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val portfolioFlowEventService: PortfolioFlowEventService,
) {
    @Transactional
    open fun setComeCotasEnabled(
        holdingId: UUID,
        accountId: UUID,
        enabled: Boolean?,
    ): Holding =
        updateFundHoldingParams(
            UpdateFundHoldingParamsCommand(
                holdingId = holdingId,
                comeCotasEnabled = enabled,
                hasComeCotasEnabled = true,
            ),
            accountId,
        )

    @Transactional
    open fun updateFundHoldingParams(
        command: UpdateFundHoldingParamsCommand,
        accountId: UUID,
    ): Holding {
        val holding =
            holdingRepository.findById(command.holdingId, accountId)
                ?: throw NoSuchElementException("Holding ${command.holdingId} not found")
        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NoSuchElementException("Asset ${holding.assetId} not found")
        if (command.hasComeCotasEnabled && asset.assetType != AssetType.MUTUAL_FUND) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "holdingId",
                parameters = mapOf("reason" to "Come-cotas override is only valid for mutual funds"),
            )
        }

        val updated =
            holdingRepository.update(
                holding.copy(
                    comeCotasEnabled =
                        if (command.hasComeCotasEnabled) {
                            command.comeCotasEnabled
                        } else {
                            holding.comeCotasEnabled
                        },
                    administrationFeeAnnualPercent =
                        if (command.hasAdministrationFeeAnnualPercent) {
                            command.administrationFeeAnnualPercent
                        } else {
                            holding.administrationFeeAnnualPercent
                        },
                    updatedAt = Instant.now(),
                ),
            )

        val recalculateFrom =
            investmentTransactionRepository
                .findByHolding(updated.id ?: command.holdingId, accountId)
                .minOfOrNull { it.tradeDate }
                ?: LocalDate.now()
        portfolioFlowEventService.requestRecalculation(
            financialAccountId = updated.financialAccountId,
            accountId = accountId,
            recalculateFrom = recalculateFrom,
            triggeredBy = "fund_holding_params:${updated.id ?: command.holdingId}",
        )
        return updated
    }
}
