package io.github.salomax.neotool.banking.model.dto.account

import io.github.salomax.neotool.banking.domain.account.BankData
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.CreditCardLevel
import io.github.salomax.neotool.banking.domain.account.CreditCardNetwork
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Command to create a new financial account.
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

sealed interface UpdateField<out T> {
    data object Absent : UpdateField<Nothing>

    data class Present<T>(
        val value: T?,
    ) : UpdateField<T>
}

fun <T> UpdateField<T>.resolve(currentValue: T?): T? =
    when (this) {
        UpdateField.Absent -> currentValue
        is UpdateField.Present -> value
    }

data class BankDataPatch(
    val branchCode: UpdateField<String> = UpdateField.Absent,
    val number: UpdateField<String> = UpdateField.Absent,
    val checkDigit: UpdateField<String> = UpdateField.Absent,
    val maskedNumber: UpdateField<String> = UpdateField.Absent,
)

data class CardDataPatch(
    val maskedNumber: UpdateField<String> = UpdateField.Absent,
    val bin: UpdateField<String> = UpdateField.Absent,
    val cardNetwork: UpdateField<CreditCardNetwork> = UpdateField.Absent,
    val cardLevel: UpdateField<CreditCardLevel> = UpdateField.Absent,
    val creditLimit: UpdateField<BigDecimal> = UpdateField.Absent,
    val availableCreditLimit: UpdateField<BigDecimal> = UpdateField.Absent,
    val minimumPaymentPercentage: UpdateField<BigDecimal> = UpdateField.Absent,
    val closingDay: UpdateField<Int> = UpdateField.Absent,
    val dueDay: UpdateField<Int> = UpdateField.Absent,
    val linkedBankAccountId: UpdateField<UUID> = UpdateField.Absent,
    val isMultiCard: UpdateField<Boolean> = UpdateField.Absent,
)

/**
 * Command to update an existing financial account.
 * Either [issuerId] (from the issuers list) or [issuerName] (custom) must be provided.
 */
data class UpdateFinancialAccountCommand(
    val type: FinancialAccountType,
    val subtype: FinancialAccountSubtype,
    val name: UpdateField<String> = UpdateField.Absent,
    val issuerId: UUID? = null,
    val issuerName: String? = null,
    val themeColor: UpdateField<String> = UpdateField.Absent,
    val currencyCode: UpdateField<String> = UpdateField.Absent,
    val includeInNetWorth: UpdateField<Boolean> = UpdateField.Absent,
    val includeInDashboard: UpdateField<Boolean> = UpdateField.Absent,
    val includeCashInAllocation: UpdateField<Boolean> = UpdateField.Absent,
    val bankData: BankDataPatch? = null,
    val cardData: CardDataPatch? = null,
    val initialBalance: UpdateField<BigDecimal> = UpdateField.Absent,
    val initialBalanceDate: UpdateField<LocalDate> = UpdateField.Absent,
)
