package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.model.dto.account.CardDataPatch
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.model.dto.account.UpdateField
import io.github.salomax.neotool.banking.model.dto.account.UpdateFinancialAccountCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("FinancialAccount multi-card IT")
class FinancialAccountMultiCardIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var financialAccountService: FinancialAccountService

    @Inject
    lateinit var financialAccountRepository: FinancialAccountRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID

    @BeforeEach
    fun initContextIds() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
    }

    @Test
    fun `create multi-card account nulls masked number and excludes account from last-four matching`() {
        val created =
            financialAccountService.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                    name = "Cartao multiplo",
                    issuerName = "Banco Teste",
                    cardData =
                        CardData(
                            maskedNumber = "1234",
                            closingDay = 7,
                            dueDay = 15,
                            isMultiCard = true,
                        ),
                ),
                accountId,
                userId,
            )

        assertThat(created.cardData).isNotNull
        assertThat(created.cardData!!.isMultiCard).isTrue()
        assertThat(created.cardData!!.maskedNumber).isNull()
        assertThat(created.cardLastFourDigits).isNull()

        val listed = financialAccountService.listByAccount(accountId)
        val listedCreated = listed.first { it.id == created.id }
        assertThat(listedCreated.cardData!!.isMultiCard).isTrue()

        val matches = financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234")
        assertThat(matches).noneMatch { it.id == created.id }
    }

    @Test
    fun `toggle multi-card off with masked number makes account matchable by last four digits`() {
        val created =
            financialAccountService.create(
                CreateFinancialAccountCommand(
                    type = FinancialAccountType.CREDIT,
                    subtype = FinancialAccountSubtype.CREDIT_CARD,
                    name = "Cartao multiplo",
                    issuerName = "Banco Teste",
                    cardData =
                        CardData(
                            closingDay = 7,
                            dueDay = 15,
                            isMultiCard = true,
                        ),
                ),
                accountId,
                userId,
            )

        val updated =
            financialAccountService.update(
                financialAccountId = requireNotNull(created.id),
                command =
                    UpdateFinancialAccountCommand(
                        type = FinancialAccountType.CREDIT,
                        subtype = FinancialAccountSubtype.CREDIT_CARD,
                        issuerName = "Banco Teste",
                        cardData =
                            CardDataPatch(
                                isMultiCard = UpdateField.Present(false),
                                maskedNumber = UpdateField.Present("1234"),
                            ),
                    ),
                accountId = accountId,
                userId = userId,
            )

        assertThat(updated.cardData).isNotNull
        assertThat(updated.cardData!!.isMultiCard).isFalse()
        assertThat(updated.cardData!!.maskedNumber).isEqualTo("1234")
        assertThat(updated.cardLastFourDigits).isEqualTo("1234")

        val matches = financialAccountRepository.findCardAccountsByLastFourDigits(accountId, "1234")
        assertThat(matches).extracting<UUID> { it.id }.contains(requireNotNull(created.id))
    }
}
