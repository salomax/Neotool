package io.github.salomax.neotool.banking.domain.account

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Bank-specific data (type = BANK). One-to-one with [FinancialAccount] when type is BANK.
 */
data class BankData(
    val branchCode: String? = null,
    val number: String? = null,
    val checkDigit: String? = null,
    val maskedNumber: String? = null,
)

/**
 * Card-specific data (type = CREDIT or PAYMENT). One-to-one with [FinancialAccount] when subtype is a card (e.g. CREDIT_CARD, PREPAID_CARD).
 * creditLimit, closingDay, dueDay are nullable in DB; NOT NULL for credit cards is enforced at the service layer.
 */
data class CardData(
    val maskedNumber: String? = null,
    val bin: String? = null,
    val cardNetwork: CreditCardNetwork? = null,
    val cardLevel: CreditCardLevel? = null,
    val creditLimit: BigDecimal? = null,
    val availableCreditLimit: BigDecimal? = null,
    val minimumPaymentPercentage: BigDecimal? = null,
    val closingDay: Int? = null,
    val dueDay: Int? = null,
    /** Optional link to a bank account used to auto-create the debit leg of a bill-payment transfer. */
    val linkedBankAccountId: UUID? = null,
)

/**
 * Domain model for a personal finance account (banking.financial_accounts).
 * Ownership: accountId (tenant); createdByUserId is audit only.
 */
data class FinancialAccount(
    val id: UUID? = null,
    val accountId: UUID,
    val createdByUserId: UUID,
    val type: FinancialAccountType,
    val subtype: FinancialAccountSubtype,
    val name: String,
    val currencyCode: String = "BRL",
    val includeInNetWorth: Boolean = true,
    val includeInDashboard: Boolean = true,
    /** When true, custodian cash is included in investment allocation % for this account. */
    val includeCashInAllocation: Boolean = true,
    // FK to banking.issuers; null = custom/unlisted institution
    val issuerId: UUID? = null,
    val themeColor: String? = null,
    val logoKey: String? = null,
    val issuerName: String? = null,
    val issuerCountry: String? = null,
    val issuerCompeCode: String? = null,
    val issuerIspb: String? = null,
    val cardLastFourDigits: String? = null,
    val bankData: BankData? = null,
    val cardData: CardData? = null,
    val connectionStatus: ConnectionStatus? = null,
    val consentExpiresAt: Instant? = null,
    val lastSyncAt: Instant? = null,
    val providerItemId: String? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
