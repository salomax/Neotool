package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.RecordAmortizationCommand
import io.github.salomax.neotool.banking.model.dto.investment.RecordAmortizationResult
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.util.UUID

private val AMORTIZABLE_ASSET_TYPES: Set<AssetType> =
    setOf(
        AssetType.DEBENTURES,
        AssetType.CRI,
        AssetType.CRA,
        AssetType.TREASURY,
        AssetType.REAL_ESTATE_FUND,
        AssetType.AGRIBUSINESS_FUND,
        AssetType.ETF_FIXED_INCOME,
    )

@Singleton
open class RecordAmortizationService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentTransactionService: InvestmentTransactionService,
    private val transactionRepository: TransactionRepository,
) {
    @Transactional
    open fun confirm(
        command: RecordAmortizationCommand,
        accountId: UUID,
        userId: UUID,
    ): RecordAmortizationResult {
        val holding =
            holdingRepository.findById(command.holdingId, accountId)
                ?: throw NotFoundException(
                    errorCode = CommonErrorCode.RESOURCE_NOT_FOUND,
                    field = "holdingId",
                    parameters = mapOf("holdingId" to command.holdingId),
                )
        val holdingId =
            holding.id
                ?: throw ValidationException(
                    errorCode = CommonErrorCode.INVALID_STATE,
                    field = "holdingId",
                    parameters = mapOf("holdingId" to command.holdingId),
                )

        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NotFoundException(
                    errorCode = CommonErrorCode.RESOURCE_NOT_FOUND,
                    field = "assetId",
                    parameters = mapOf("assetId" to holding.assetId),
                )
        if (asset.assetType !in AMORTIZABLE_ASSET_TYPES) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_STATE,
                field = "holdingId",
                parameters =
                    mapOf(
                        "holdingId" to holdingId,
                        "assetType" to asset.assetType.name,
                    ),
            )
        }

        if (command.grossValue <= BigDecimal.ZERO) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "grossValue",
                parameters = mapOf("grossValue" to command.grossValue),
            )
        }
        if (command.netValue < BigDecimal.ZERO) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "netValue",
                parameters = mapOf("netValue" to command.netValue),
            )
        }
        if (command.netValue > command.grossValue) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "netValue",
                parameters =
                    mapOf(
                        "netValue" to command.netValue,
                        "grossValue" to command.grossValue,
                    ),
            )
        }

        val amortizationTransaction =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = holding.financialAccountId,
                        assetId = holding.assetId,
                        direction = InvestmentTransactionDirection.OUTFLOW,
                        type = InvestmentTransactionType.AMORTIZATION,
                        grossValue = command.grossValue,
                        netValue = command.netValue,
                        incomeTax = command.incomeTax,
                        iof = command.iof,
                        tradeDate = command.tradeDate,
                        settlementDate = command.tradeDate,
                        source = InvestmentSource.MANUAL,
                        providerInvestmentId = holding.providerInvestmentId,
                        cashDestinationAccountId = command.destinationAccountId,
                    ),
                accountId = accountId,
                userId = userId,
            )

        val bankingTransaction =
            if (command.destinationAccountId != null) {
                amortizationTransaction.id?.let { investmentTxId ->
                    transactionRepository
                        .findByInvestmentTransactionIds(
                            listOf(investmentTxId),
                            accountId,
                        ).firstOrNull()
                }
            } else {
                null
            }

        return RecordAmortizationResult(
            amortizationTransaction = amortizationTransaction,
            bankingTransaction = bankingTransaction,
        )
    }
}
