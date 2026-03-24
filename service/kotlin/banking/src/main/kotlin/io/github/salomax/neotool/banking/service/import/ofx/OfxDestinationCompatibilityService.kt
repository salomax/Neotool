package io.github.salomax.neotool.banking.service.import.ofx

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.service.import.parser.OfxStatementIntent
import io.github.salomax.neotool.banking.service.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.BusinessRuleException
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class OfxDestinationCompatibilityService(
    private val financialAccountRepository: FinancialAccountRepository,
) {
    companion object {
        private val BANK_STATEMENT_SUBTYPES =
            setOf(
                FinancialAccountSubtype.CONTA_CORRENTE,
                FinancialAccountSubtype.CONTA_POUPANCA,
                FinancialAccountSubtype.CONTA_SALARIO,
                FinancialAccountSubtype.CONTA_DIGITAL,
                FinancialAccountSubtype.CONTA_CONJUNTA,
            )
    }

    fun validateOrThrow(
        financialAccountId: UUID,
        accountId: UUID,
        statementIntent: OfxStatementIntent,
    ) {
        if (statementIntent == OfxStatementIntent.UNKNOWN) return
        val destination =
            financialAccountRepository.findByAccountId(accountId)
                .firstOrNull { it.id == financialAccountId }
                ?: return

        val compatible =
            when (statementIntent) {
                OfxStatementIntent.BANK_STATEMENT -> destination.subtype in BANK_STATEMENT_SUBTYPES
                OfxStatementIntent.CREDIT_CARD_STATEMENT -> destination.subtype in CreditCardBillService.CARD_SUBTYPES
                OfxStatementIntent.UNKNOWN -> true
            }

        if (!compatible) {
            throw BusinessRuleException(
                errorCode = BankingErrorCode.IMPORT_OFX_DESTINATION_MISMATCH,
                parameters =
                    mapOf(
                        "statementIntent" to statementIntent.name,
                        "financialAccountId" to financialAccountId.toString(),
                        "financialAccountSubtype" to destination.subtype.name,
                    ),
            )
        }
    }
}
