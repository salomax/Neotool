package io.github.salomax.neotool.banking.graphql.dto

import io.github.salomax.neotool.banking.domain.account.ConnectionStatus
import io.github.salomax.neotool.banking.domain.account.CreditCardLevel
import io.github.salomax.neotool.banking.domain.account.CreditCardNetwork
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import java.time.OffsetDateTime
import java.util.UUID

data class BankAccountDataDTO(
    val branchCode: String?,
    val number: String?,
    val checkDigit: String?,
    val maskedNumber: String?,
)

data class CardDataDTO(
    val maskedNumber: String?,
    val bin: String?,
    val cardNetwork: CreditCardNetwork?,
    val cardLevel: CreditCardLevel?,
    val creditLimit: Double?,
    val availableCreditLimit: Double?,
    val minimumPaymentPercentage: Double?,
    val closingDay: Int?,
    val dueDay: Int?,
    val linkedBankAccountId: UUID?,
)

data class FinancialAccountDTO(
    val id: UUID,
    val type: FinancialAccountType,
    val subtype: FinancialAccountSubtype,
    val name: String,
    val currencyCode: String,
    val includeInNetWorth: Boolean,
    val includeInDashboard: Boolean,
    val themeColor: String?,
    val issuer: IssuerDTO?,
    val issuerName: String?,
    val bankData: BankAccountDataDTO?,
    val cardData: CardDataDTO?,
    val connectionStatus: ConnectionStatus?,
    val consentExpiresAt: OffsetDateTime?,
    val lastSyncAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
