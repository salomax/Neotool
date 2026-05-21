package io.github.salomax.neotool.banking.service.import.ofx

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.service.import.parser.OfxStatementIntent
import io.github.salomax.neotool.common.error.BusinessRuleException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("OfxDestinationCompatibilityService")
class OfxDestinationCompatibilityServiceTest {
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var service: OfxDestinationCompatibilityService

    private val accountId = UUID.randomUUID()
    private val createdBy = UUID.randomUUID()
    private val bankFinancialAccountId = UUID.randomUUID()
    private val cardFinancialAccountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        financialAccountRepository = mock()
        service = OfxDestinationCompatibilityService(financialAccountRepository)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(
                financialAccount(
                    id = bankFinancialAccountId,
                    type = FinancialAccountType.BANK,
                    subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                ),
                financialAccount(
                    id = cardFinancialAccountId,
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                ),
            ),
        )
    }

    @Test
    fun `bank statement intent is compatible with bank account subtype`() {
        assertThatCode {
            service.validateOrThrow(
                financialAccountId = bankFinancialAccountId,
                accountId = accountId,
                statementIntent = OfxStatementIntent.BANK_STATEMENT,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `credit card statement intent is compatible with credit card subtype`() {
        assertThatCode {
            service.validateOrThrow(
                financialAccountId = cardFinancialAccountId,
                accountId = accountId,
                statementIntent = OfxStatementIntent.CREDIT_CARD_STATEMENT,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `bank statement intent with credit card destination throws mismatch error`() {
        assertThatThrownBy {
            service.validateOrThrow(
                financialAccountId = cardFinancialAccountId,
                accountId = accountId,
                statementIntent = OfxStatementIntent.BANK_STATEMENT,
            )
        }.isInstanceOf(BusinessRuleException::class.java)
            .extracting("errorCode")
            .isEqualTo(BankingErrorCode.IMPORT_OFX_DESTINATION_MISMATCH)
    }

    @Test
    fun `credit card statement intent with bank destination throws mismatch error`() {
        val thrown =
            catchThrowable {
                service.validateOrThrow(
                    financialAccountId = bankFinancialAccountId,
                    accountId = accountId,
                    statementIntent = OfxStatementIntent.CREDIT_CARD_STATEMENT,
                )
            } as BusinessRuleException

        assertThat(thrown.errorCode).isEqualTo(BankingErrorCode.IMPORT_OFX_DESTINATION_MISMATCH)
        assertThat(thrown.parameters["statementIntent"]).isEqualTo(OfxStatementIntent.CREDIT_CARD_STATEMENT.name)
        assertThat(thrown.parameters["financialAccountSubtype"]).isEqualTo(FinancialAccountSubtype.CONTA_CORRENTE.name)
    }

    @Test
    fun `unknown statement intent is ignored`() {
        assertThatCode {
            service.validateOrThrow(
                financialAccountId = cardFinancialAccountId,
                accountId = accountId,
                statementIntent = OfxStatementIntent.UNKNOWN,
            )
        }.doesNotThrowAnyException()
    }

    private fun financialAccount(
        id: UUID,
        type: FinancialAccountType,
        subtype: FinancialAccountSubtype,
    ): FinancialAccount =
        FinancialAccount(
            id = id,
            accountId = accountId,
            createdByUserId = createdBy,
            type = type,
            subtype = subtype,
            name = "Account $subtype",
            currencyCode = "BRL",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
