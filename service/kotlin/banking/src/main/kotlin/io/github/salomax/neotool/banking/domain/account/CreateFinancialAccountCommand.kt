package io.github.salomax.neotool.banking.domain.account

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Command to create a new [FinancialAccount].
 * Either [issuerId] (from the issuers list) or [issuerName] (custom) must be provided.
 */
data class CreateFinancialAccountCommand(
    val type: FinancialAccountType,
    val subtype: FinancialAccountSubtype,
    /** Display name. Defaults to issuer name when null. */
    val name: String? = null,
    /** FK to banking.issuers. Mutually exclusive with [issuerName]. */
    val issuerId: UUID? = null,
    /** Free-text institution name when [issuerId] is null. */
    val issuerName: String? = null,
    val themeColor: String? = null,
    val currencyCode: String? = null,
    val includeInNetWorth: Boolean? = null,
    val includeInDashboard: Boolean? = null,
    val includeCashInAllocation: Boolean? = null,
    val bankData: BankData? = null,
    val cardData: CardData? = null,
    /** Non-zero account balance on [initialBalanceDate]. Creates a BALANCE_ADJUSTMENT transaction when provided. */
    val initialBalance: BigDecimal? = null,
    /** Required when [initialBalance] is provided. */
    val initialBalanceDate: LocalDate? = null,
)

/**
 * Command to update an existing [FinancialAccount].
 * Either [issuerId] (from the issuers list) or [issuerName] (custom) must be provided.
 */
data class UpdateFinancialAccountCommand(
    val type: FinancialAccountType,
    val subtype: FinancialAccountSubtype,
    val name: String? = null,
    val issuerId: UUID? = null,
    val issuerName: String? = null,
    val themeColor: String? = null,
    val currencyCode: String? = null,
    val includeInNetWorth: Boolean? = null,
    val includeInDashboard: Boolean? = null,
    val includeCashInAllocation: Boolean? = null,
    val bankData: BankData? = null,
    val cardData: CardData? = null,
)
