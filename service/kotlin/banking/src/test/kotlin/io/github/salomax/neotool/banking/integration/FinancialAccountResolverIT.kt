package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountResolver
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("FinancialAccountResolver integration")
class FinancialAccountResolverIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var financialAccountResolver: FinancialAccountResolver

    @Inject
    lateinit var financialAccountService: FinancialAccountService

    private lateinit var accountId: UUID
    private lateinit var userId: UUID

    @BeforeEach
    fun initContextIds() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
    }

    @Test
    fun `list omits initial balance but detail loads it on demand`() {
        val initialBalanceDate = LocalDate.now()
        val account =
            financialAccountService.create(
                command =
                    CreateFinancialAccountCommand(
                        type = FinancialAccountType.BANK,
                        subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                        name = "Conta editavel",
                        issuerName = "Banco Teste",
                        initialBalance = BigDecimal("825.00"),
                        initialBalanceDate = initialBalanceDate,
                    ),
                accountId = accountId,
                userId = userId,
            )

        val principal = principal()
        val listed = financialAccountResolver.listByAccount(principal).single { it.id == account.id }
        val detailed = financialAccountResolver.getById(requireNotNull(account.id).toString(), principal)

        assertThat(listed.initialBalance).isNull()
        assertThat(listed.initialBalanceDate).isNull()
        assertThat(detailed).isNotNull
        assertThat(detailed!!.initialBalance).isEqualTo(825.0)
        assertThat(detailed.initialBalanceDate).isEqualTo(initialBalanceDate)
    }

    @Test
    fun `list handles custom issuer accounts without initial balance`() {
        val account =
            financialAccountService.create(
                command =
                    CreateFinancialAccountCommand(
                        type = FinancialAccountType.BANK,
                        subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                        name = "Conta sem saldo",
                        issuerName = "Instituicao manual",
                    ),
                accountId = accountId,
                userId = userId,
            )

        val listed = financialAccountResolver.listByAccount(principal()).single { it.id == account.id }

        assertThat(listed.name).isEqualTo("Conta sem saldo")
        assertThat(listed.issuer).isNull()
        assertThat(listed.issuerName).isEqualTo("Instituicao manual")
        assertThat(listed.initialBalance).isNull()
        assertThat(listed.initialBalanceDate).isNull()
    }

    @Test
    fun `update returns refreshed initial balance after editing existing balance adjustment`() {
        val originalDate = LocalDate.now().minusDays(1)
        val updatedDate = LocalDate.now()
        val account =
            financialAccountService.create(
                command =
                    CreateFinancialAccountCommand(
                        type = FinancialAccountType.BANK,
                        subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                        name = "Conta com saldo inicial",
                        issuerName = "Banco Teste",
                        initialBalance = BigDecimal("1000.00"),
                        initialBalanceDate = originalDate,
                    ),
                accountId = accountId,
                userId = userId,
            )

        val updated =
            financialAccountResolver.update(
                id = requireNotNull(account.id).toString(),
                input =
                    mapOf(
                        "type" to "BANK",
                        "subtype" to "CONTA_CORRENTE",
                        "issuerName" to "Banco Teste",
                        "initialBalance" to 1350.0,
                        "initialBalanceDate" to updatedDate.toString(),
                    ),
                principal = principal(),
            )

        assertThat(updated.initialBalance).isEqualTo(1350.0)
        assertThat(updated.initialBalanceDate).isEqualTo(updatedDate)
    }

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )
}
