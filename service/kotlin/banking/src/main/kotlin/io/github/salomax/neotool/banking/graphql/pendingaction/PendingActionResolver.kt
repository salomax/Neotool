package io.github.salomax.neotool.banking.graphql.pendingaction

import io.github.salomax.neotool.banking.domain.investment.HoldingCloseReason
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.graphql.investment.BrokerNoteDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentTransactionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import io.github.salomax.neotool.banking.service.investment.HoldingMaturityConfirmCommand
import io.github.salomax.neotool.banking.service.investment.HoldingMaturityConfirmService
import io.github.salomax.neotool.banking.service.investment.MaturityCashMovementCommand
import io.github.salomax.neotool.banking.service.pendingaction.AccountPendingActionService
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Singleton
class PendingActionResolver(
    private val accountPendingActionService: AccountPendingActionService,
    private val holdingMaturityConfirmService: HoldingMaturityConfirmService,
) {
    fun pendingActions(
        status: String?,
        principal: RequestPrincipal,
    ): List<AccountPendingActionDTO> {
        val accountId = requireAccountId(principal)
        val st = status?.let(AccountPendingActionStatus::valueOf)
        return accountPendingActionService
            .listForAccount(accountId = accountId, status = st)
            .map { it.toDto() }
    }

    @Suppress("UNCHECKED_CAST")
    fun confirmHoldingMaturity(
        command: Map<String, Any?>,
        principal: RequestPrincipal,
    ): ConfirmHoldingMaturityResultDTO {
        val accountId = requireAccountId(principal)
        val userId = requireNotNull(principal.userId) { "No user in token" }

        fun Any?.toLocalDate(): LocalDate =
            when (this) {
                is LocalDate -> this
                is String -> LocalDate.parse(this)
                else -> error("Expected LocalDate or ISO date string")
            }

        val cashRaw = command["cashMovement"] as? Map<String, Any?>
        val cashMovement =
            cashRaw?.let { m ->
                MaturityCashMovementCommand(
                    financialAccountId = UUID.fromString(m["financialAccountId"] as String),
                    amount = BigDecimal((m["amount"] as Number).toString()),
                    date = m["date"].toLocalDate(),
                )
            }

        val cmd =
            HoldingMaturityConfirmCommand(
                holdingId = UUID.fromString(command["holdingId"] as String),
                maturityDate = command["maturityDate"].toLocalDate(),
                grossValue = BigDecimal((command["grossValue"] as Number).toString()),
                netValue = BigDecimal((command["netValue"] as Number).toString()),
                incomeTax = (command["incomeTax"] as? Number)?.let { BigDecimal(it.toString()) },
                iof = (command["iof"] as? Number)?.let { BigDecimal(it.toString()) },
                closeReason = HoldingCloseReason.valueOf(command["closeReason"] as String),
                cashMovement = cashMovement,
            )

        val result =
            holdingMaturityConfirmService.confirm(
                command = cmd,
                accountId = accountId,
                userId = userId,
            )

        return ConfirmHoldingMaturityResultDTO(
            maturityTransaction = result.maturityTransaction.toInvestmentTxDto(),
            bankingTransaction = result.bankingTransaction?.toBankingTxDto(),
            pendingAction = result.pendingAction.toDto(),
        )
    }

    fun dismissPendingAction(
        id: String,
        principal: RequestPrincipal,
    ): AccountPendingActionDTO {
        val accountId = requireAccountId(principal)
        return accountPendingActionService
            .dismiss(UUID.fromString(id), accountId)
            .toDto()
    }

    private fun AccountPendingAction.toDto(): AccountPendingActionDTO =
        AccountPendingActionDTO(
            id = requireNotNull(id),
            type = type.name,
            severity = severity.name,
            status = status.name,
            entityType = entityType,
            entityId = entityId,
            payload = payload,
            createdAt = createdAt,
            expiresAt = expiresAt,
        )

    private fun InvestmentTransaction.toInvestmentTxDto(): InvestmentTransactionDTO =
        InvestmentTransactionDTO(
            id = requireNotNull(id),
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            direction = direction,
            type = type,
            typeAdditionalInfo = typeAdditionalInfo,
            quantity = quantity?.toDouble(),
            unitPrice = unitPrice?.toDouble(),
            grossValue = grossValue.toDouble(),
            netValue = netValue?.toDouble(),
            incomeTax = incomeTax?.toDouble(),
            iof = iof?.toDouble(),
            indexerPercentage = indexerPercentage?.toDouble(),
            fixedAnnualRate = fixedAnnualRate?.toDouble(),
            priceFactor = priceFactor?.toDouble(),
            tradeDate = tradeDate,
            settlementDate = settlementDate,
            source = source,
            brokerNote = brokerNote?.toBrokerNoteDto(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun io.github.salomax.neotool.banking.domain.investment.BrokerNote.toBrokerNoteDto(): BrokerNoteDTO =
        BrokerNoteDTO(
            brokerNoteNumber = brokerNoteNumber,
            grossValue = grossValue.toDouble(),
            brokerageFee = brokerageFee.toDouble(),
            clearingSettlementFee = clearingSettlementFee.toDouble(),
            clearingRegistrationFee = clearingRegistrationFee.toDouble(),
            stockExchangeAssetTradeNoticeFee = stockExchangeAssetTradeNoticeFee.toDouble(),
            stockExchangeFee = stockExchangeFee.toDouble(),
            clearingCustodyFee = clearingCustodyFee.toDouble(),
            incomeTax = incomeTax.toDouble(),
            taxes = taxes.toDouble(),
            netValue = netValue.toDouble(),
            createdAt = createdAt,
        )

    private fun Transaction.toBankingTxDto(): TransactionDTO =
        TransactionDTO(
            id = requireNotNull(id),
            financialAccountId = financialAccountId,
            belongsToUserId = belongsToUserId,
            belongsToName = belongsToName,
            creditDebitType = creditDebitType,
            type = type,
            status = status,
            source = source,
            amount = amount.toDouble(),
            currencyCode = currencyCode,
            amountBrl = amountBrl?.toDouble(),
            date = date,
            transactionDatetime = transactionDatetime,
            description = description,
            notes = notes,
            tags = tags,
            category = null,
            categorySource = categorySource,
            merchant = null,
            counterpartyName = counterpartyName,
            counterpartyDocument = counterpartyDocument,
            counterpartyBankCode = counterpartyBankCode,
            counterpartyAccountNumber = counterpartyAccountNumber,
            paymentReason = paymentReason,
            transferPeerTransactionId = transferPeerTransactionId,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            billId = billId,
            cardIdentifier = cardIdentifier,
            payeeMcc = payeeMcc,
            billPostDate = billPostDate,
            appliedRuleIds = appliedRuleIds,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }
}
