package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.investment.TransferCustodyCommand
import io.github.salomax.neotool.banking.model.dto.investment.TransferCustodyResult
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.util.UUID

@Singleton
open class TransferCustodyService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val investmentTransactionService: InvestmentTransactionService,
) {
    /**
     * Transfers a holding (full or partial) between two custody accounts owned by the same account.
     * This operation records paired OUTFLOW/INFLOW legs atomically while preserving average cost.
     */
    @Transactional
    open fun transfer(
        command: TransferCustodyCommand,
        accountId: UUID,
        userId: UUID,
    ): TransferCustodyResult {
        val sourceHolding =
            holdingRepository.findById(command.sourceHoldingId, accountId)
                ?: throw NotFoundException(
                    errorCode = BankingErrorCode.TRANSFER_CUSTODY_HOLDING_NOT_FOUND,
                    field = "sourceHoldingId",
                    parameters = mapOf("sourceHoldingId" to command.sourceHoldingId),
                )

        if (command.destinationFinancialAccountId == sourceHolding.financialAccountId) {
            throw ValidationException(
                errorCode = BankingErrorCode.TRANSFER_CUSTODY_SAME_ACCOUNT_NOT_ALLOWED,
                field = "destinationFinancialAccountId",
                parameters =
                    mapOf(
                        "destinationFinancialAccountId" to command.destinationFinancialAccountId,
                        "sourceFinancialAccountId" to sourceHolding.financialAccountId,
                    ),
            )
        }

        if (
            !financialAccountRepository.existsByIdAndAccountId(
                command.destinationFinancialAccountId,
                accountId,
            )
        ) {
            throw ValidationException(
                errorCode = BankingErrorCode.TRANSFER_CUSTODY_DESTINATION_ACCOUNT_INVALID,
                field = "destinationFinancialAccountId",
                parameters =
                    mapOf(
                        "destinationFinancialAccountId" to command.destinationFinancialAccountId,
                    ),
            )
        }

        val quantity = command.quantity ?: sourceHolding.quantity
        if (quantity <= BigDecimal.ZERO) {
            throw ValidationException(
                errorCode = BankingErrorCode.TRANSFER_CUSTODY_QUANTITY_MUST_BE_POSITIVE,
                field = "quantity",
                parameters = mapOf("quantity" to quantity),
            )
        }
        if (sourceHolding.quantity < quantity) {
            throw ValidationException(
                errorCode = BankingErrorCode.TRANSFER_CUSTODY_QUANTITY_EXCEEDS_SOURCE_HOLDING,
                field = "quantity",
                parameters =
                    mapOf(
                        "requestedQuantity" to quantity,
                        "availableQuantity" to sourceHolding.quantity,
                    ),
            )
        }

        val asset =
            assetRepository.findById(sourceHolding.assetId)
                ?: throw NotFoundException(
                    errorCode = BankingErrorCode.TRANSFER_CUSTODY_ASSET_NOT_FOUND,
                    field = "assetId",
                    parameters = mapOf("assetId" to sourceHolding.assetId),
                )

        val transferType =
            if (asset.assetType == AssetType.MUTUAL_FUND) {
                InvestmentTransactionType.QUOTA_TRANSFER
            } else {
                InvestmentTransactionType.CUSTODY_TRANSFER
            }

        val transferGrossValue = quantity * sourceHolding.averageCost

        val outflowTransaction =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = sourceHolding.financialAccountId,
                        assetId = sourceHolding.assetId,
                        direction = InvestmentTransactionDirection.OUTFLOW,
                        type = transferType,
                        grossValue = transferGrossValue,
                        netValue = transferGrossValue,
                        quantity = quantity,
                        unitPrice = sourceHolding.averageCost,
                        tradeDate = command.tradeDate,
                        settlementDate = command.tradeDate,
                        source = InvestmentSource.MANUAL,
                    ),
                accountId = accountId,
                userId = userId,
            )

        val inflowTransaction =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = command.destinationFinancialAccountId,
                        assetId = sourceHolding.assetId,
                        direction = InvestmentTransactionDirection.INFLOW,
                        type = transferType,
                        grossValue = transferGrossValue,
                        netValue = transferGrossValue,
                        quantity = quantity,
                        unitPrice = sourceHolding.averageCost,
                        tradeDate = command.tradeDate,
                        settlementDate = command.tradeDate,
                        source = InvestmentSource.MANUAL,
                    ),
                accountId = accountId,
                userId = userId,
            )

        return TransferCustodyResult(
            outflowTransaction = outflowTransaction,
            inflowTransaction = inflowTransaction,
        )
    }
}
