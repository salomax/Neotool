package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.investment.RecordStockLendingCommand
import io.github.salomax.neotool.banking.model.dto.investment.RecordStockLendingResult
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.util.UUID

private val LENDABLE_ASSET_TYPES: Set<AssetType> =
    setOf(
        AssetType.STOCK,
        AssetType.ETF,
        AssetType.BDR,
        AssetType.REAL_ESTATE_FUND,
        AssetType.AGRIBUSINESS_FUND,
    )

@Singleton
open class RecordStockLendingService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentTransactionService: InvestmentTransactionService,
    private val transactionRepository: TransactionRepository,
) {
    @Transactional
    open fun confirm(
        command: RecordStockLendingCommand,
        accountId: UUID,
        userId: UUID,
    ): RecordStockLendingResult {
        val holding =
            holdingRepository.findById(command.holdingId, accountId)
                ?: throw NotFoundException(
                    errorCode = BankingErrorCode.RECORD_STOCK_LENDING_HOLDING_NOT_FOUND,
                    field = "holdingId",
                    parameters = mapOf("holdingId" to command.holdingId),
                )

        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NotFoundException(
                    errorCode = BankingErrorCode.RECORD_STOCK_LENDING_ASSET_NOT_FOUND,
                    field = "assetId",
                    parameters = mapOf("assetId" to holding.assetId),
                )
        if (asset.assetType !in LENDABLE_ASSET_TYPES) {
            throw ValidationException(
                errorCode = BankingErrorCode.RECORD_STOCK_LENDING_ASSET_TYPE_NOT_SUPPORTED,
                field = "holdingId",
                parameters =
                    mapOf(
                        "holdingId" to command.holdingId,
                        "assetType" to asset.assetType.name,
                    ),
            )
        }

        if (command.grossValue <= BigDecimal.ZERO) {
            throw ValidationException(
                errorCode = BankingErrorCode.RECORD_STOCK_LENDING_GROSS_VALUE_MUST_BE_POSITIVE,
                field = "grossValue",
                parameters = mapOf("grossValue" to command.grossValue),
            )
        }
        if (command.netValue < BigDecimal.ZERO) {
            throw ValidationException(
                errorCode = BankingErrorCode.RECORD_STOCK_LENDING_NET_VALUE_MUST_BE_NON_NEGATIVE,
                field = "netValue",
                parameters = mapOf("netValue" to command.netValue),
            )
        }
        if (command.incomeTax < BigDecimal.ZERO) {
            throw ValidationException(
                errorCode = BankingErrorCode.RECORD_STOCK_LENDING_INCOME_TAX_MUST_BE_NON_NEGATIVE,
                field = "incomeTax",
                parameters = mapOf("incomeTax" to command.incomeTax),
            )
        }
        if (command.netValue > command.grossValue) {
            throw ValidationException(
                errorCode = BankingErrorCode.RECORD_STOCK_LENDING_NET_VALUE_EXCEEDS_GROSS_VALUE,
                field = "netValue",
                parameters =
                    mapOf(
                        "netValue" to command.netValue,
                        "grossValue" to command.grossValue,
                    ),
            )
        }

        val lendingTransaction =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = holding.financialAccountId,
                        assetId = holding.assetId,
                        direction = InvestmentTransactionDirection.INFLOW,
                        type = InvestmentTransactionType.STOCK_LENDING,
                        grossValue = command.grossValue,
                        netValue = command.netValue,
                        incomeTax = command.incomeTax,
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
                lendingTransaction.id?.let { investmentTxId ->
                    transactionRepository
                        .findByInvestmentTransactionIds(
                            listOf(investmentTxId),
                            accountId,
                        ).firstOrNull()
                }
            } else {
                null
            }

        return RecordStockLendingResult(
            lendingTransaction = lendingTransaction,
            bankingTransaction = bankingTransaction,
        )
    }
}
