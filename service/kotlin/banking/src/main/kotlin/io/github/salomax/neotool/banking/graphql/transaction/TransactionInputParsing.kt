package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateTransactionCommand
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal object TransactionInputParsing {
    @Suppress("UNCHECKED_CAST")
    fun parseCreateInput(input: Map<String, Any?>): CreateTransactionCommand =
        CreateTransactionCommand(
            financialAccountId = UUID.fromString(input["financialAccountId"] as String),
            creditDebitType = CreditDebitType.valueOf(input["creditDebitType"] as String),
            type = TransactionType.valueOf(input["type"] as String),
            amount = BigDecimal((input["amount"] as Number).toString()),
            date = getDate(input, "date") ?: throw IllegalArgumentException("date is required"),
            description = input["description"] as String,
            status =
                (input["status"] as? String)?.let { TransactionStatus.valueOf(it) }
                    ?: TransactionStatus.POSTED,
            currencyCode = (input["currencyCode"] as? String) ?: "BRL",
            notes = input["notes"] as? String,
            tags = (input["tags"] as? List<*>)?.map { it as String } ?: emptyList(),
            categoryId = (input["categoryId"] as? String)?.let { UUID.fromString(it) },
            merchantId = (input["merchantId"] as? String)?.let { UUID.fromString(it) },
            belongsToUserId = (input["belongsToUserId"] as? String)?.let { UUID.fromString(it) },
            belongsToName = input["belongsToName"] as? String,
            counterpartyName = input["counterpartyName"] as? String,
            counterpartyDocument = input["counterpartyDocument"] as? String,
            counterpartyBankCode = input["counterpartyBankCode"] as? String,
            counterpartyAccountNumber = input["counterpartyAccountNumber"] as? String,
            paymentReason = input["paymentReason"] as? String,
            installmentNumber = input["installmentNumber"] as? Int,
            totalInstallments = input["totalInstallments"] as? Int,
            cardIdentifier = input["cardIdentifier"] as? String,
            payeeMcc = input["payeeMcc"] as? Int,
        )

    @Suppress("UNCHECKED_CAST")
    fun parseUpdateInput(input: Map<String, Any?>): UpdateTransactionCommand =
        UpdateTransactionCommand(
            creditDebitType = (input["creditDebitType"] as? String)?.let { CreditDebitType.valueOf(it) },
            type = (input["type"] as? String)?.let { TransactionType.valueOf(it) },
            amount = (input["amount"] as? Number)?.let { BigDecimal(it.toString()) },
            date = getDate(input, "date"),
            description = input["description"] as? String,
            status = (input["status"] as? String)?.let { TransactionStatus.valueOf(it) },
            notes = input["notes"] as? String,
            tags = (input["tags"] as? List<*>)?.map { it as String },
            tagsProvided = input.containsKey("tags"),
            categoryId = (input["categoryId"] as? String)?.let { UUID.fromString(it) },
            categoryIdProvided = input.containsKey("categoryId"),
            merchantId = (input["merchantId"] as? String)?.let { UUID.fromString(it) },
            merchantIdProvided = input.containsKey("merchantId"),
            belongsToUserId = (input["belongsToUserId"] as? String)?.let { UUID.fromString(it) },
            belongsToName = input["belongsToName"] as? String,
            belongsToProvided = input.containsKey("belongsToUserId") || input.containsKey("belongsToName"),
            counterpartyName = input["counterpartyName"] as? String,
            counterpartyDocument = input["counterpartyDocument"] as? String,
            counterpartyBankCode = input["counterpartyBankCode"] as? String,
            counterpartyAccountNumber = input["counterpartyAccountNumber"] as? String,
            paymentReason = input["paymentReason"] as? String,
            cardIdentifier = input["cardIdentifier"] as? String,
            payeeMcc = input["payeeMcc"] as? Int,
        )

    private fun getDate(
        map: Map<String, Any?>,
        key: String,
    ): LocalDate? {
        val value = map[key] ?: return null
        return when (value) {
            is String -> LocalDate.parse(value)
            is LocalDate -> value
            else -> value.toString().let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        }
    }
}
