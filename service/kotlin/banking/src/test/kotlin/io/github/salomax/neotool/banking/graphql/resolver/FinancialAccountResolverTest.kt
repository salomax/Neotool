package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountResolver
import io.github.salomax.neotool.banking.model.transaction.TransactionEntity
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionEntityRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountDataSinceService
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("FinancialAccountResolver")
class FinancialAccountResolverTest {
    private lateinit var service: FinancialAccountService
    private lateinit var issuerRepository: IssuerRepository
    private lateinit var transactionEntityRepository: TransactionEntityRepository
    private lateinit var dataSinceService: FinancialAccountDataSinceService
    private lateinit var resolver: FinancialAccountResolver

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        service = mock()
        issuerRepository = mock()
        transactionEntityRepository = mock()
        dataSinceService = mock()
        whenever(issuerRepository.findByIds(any())).thenReturn(emptyList())
        whenever(dataSinceService.findDataSinceByFinancialAccountIds(any())).thenReturn(emptyMap())

        resolver =
            FinancialAccountResolver(
                service = service,
                issuerRepository = issuerRepository,
                transactionEntityRepository = transactionEntityRepository,
                dataSinceService = dataSinceService,
            )
    }

    @Test
    fun `listByAccount does not fetch initial balance transactions`() {
        whenever(service.listByAccount(accountId)).thenReturn(listOf(bankAccount()))

        val result = resolver.listByAccount(principal())

        assertThat(result).hasSize(1)
        assertThat(result.single().initialBalance).isNull()
        assertThat(result.single().initialBalanceDate).isNull()
        verify(issuerRepository, never()).findByIds(any())
        verify(transactionEntityRepository, never()).findInitialBalanceTransaction(any())
    }

    @Test
    fun `getById loads initial balance fields on demand`() {
        whenever(service.findById(financialAccountId, accountId)).thenReturn(bankAccount())
        whenever(
            dataSinceService.findDataSinceByFinancialAccountIds(eq(listOf(financialAccountId))),
        ).thenReturn(emptyMap())
        whenever(transactionEntityRepository.findInitialBalanceTransaction(financialAccountId.toString()))
            .thenReturn(Optional.of(initialBalanceTransaction()))

        val result = resolver.getById(financialAccountId.toString(), principal())

        assertThat(result).isNotNull
        assertThat(result!!.initialBalance).isEqualTo(825.0)
        assertThat(result.initialBalanceDate).isEqualTo(LocalDate.of(2026, 6, 3))
    }

    private fun bankAccount() =
        FinancialAccount(
            id = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            type = FinancialAccountType.BANK,
            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
            name = "Conta teste",
            issuerName = "Banco Teste",
            createdAt = now,
            updatedAt = now,
        )

    private fun initialBalanceTransaction() =
        TransactionEntity(
            id = UUID.randomUUID(),
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            creditDebitType = CreditDebitType.CREDIT,
            type = TransactionType.BALANCE_ADJUSTMENT,
            status = TransactionStatus.POSTED,
            source = TransactionSource.MANUAL,
            amount = BigDecimal("825.00"),
            date = LocalDate.of(2026, 6, 3),
            description = "Saldo inicial",
            createdAt = now,
            updatedAt = now,
        )

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )
}
