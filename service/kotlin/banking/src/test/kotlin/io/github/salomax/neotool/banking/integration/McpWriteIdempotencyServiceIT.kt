package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionOutcome
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyEntityRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.service.transaction.IdempotencyService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.service.transaction.execute
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@MicronautTest(startApplication = true)
@DisplayName("Mcp write idempotency IT")
class McpWriteIdempotencyServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var financialAccountService: FinancialAccountService

    @Inject
    lateinit var transactionService: TransactionService

    @Inject
    lateinit var idempotencyService: IdempotencyService

    @Inject
    lateinit var idempotencyEntityRepository: McpWriteIdempotencyEntityRepository

    private lateinit var accountId: UUID
    private lateinit var secondAccountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var secondFinancialAccountId: UUID

    @BeforeEach
    fun setUpAccounts() {
        accountId = UUID.randomUUID()
        secondAccountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = createBankAccount(accountId, userId, "Conta principal")
        secondFinancialAccountId = createBankAccount(secondAccountId, userId, "Conta secundária")
    }

    @Test
    fun `same key against a real mutation creates one row and replays the original result`() {
        val key = "tx-create-${UUID.randomUUID()}"

        val first =
            idempotencyService.execute<CreateTransactionOutcome>(accountId, key, "create_transaction") {
                transactionService.createTransactionOutcome(
                    command = createTransactionCommand(financialAccountId, "Mercado replay"),
                    accountId = accountId,
                    userId = userId,
                )
            }
        val replay =
            idempotencyService.execute<CreateTransactionOutcome>(accountId, key, "create_transaction") {
                transactionService.createTransactionOutcome(
                    command = createTransactionCommand(financialAccountId, "Nao deveria executar"),
                    accountId = accountId,
                    userId = userId,
                )
            }
        val distinct =
            idempotencyService.execute<CreateTransactionOutcome>(accountId, "$key-new", "create_transaction") {
                transactionService.createTransactionOutcome(
                    command = createTransactionCommand(financialAccountId, "Mercado distinct"),
                    accountId = accountId,
                    userId = userId,
                )
            }

        val storedCount = idempotencyEntityRepository.countByAccountIdAndIdempotencyKey(accountId, key)
        val storedTransaction = transactionService.findById(requireNotNull(first.transaction.id), accountId)
        val secondTransaction = transactionService.findById(requireNotNull(distinct.transaction.id), accountId)

        assertThat(storedCount).isEqualTo(1)
        assertThat(replay.transaction.id).isEqualTo(first.transaction.id)
        assertThat(storedTransaction.description).isEqualTo("Mercado replay")
        assertThat(secondTransaction.id).isNotEqualTo(storedTransaction.id)
    }

    @Test
    fun `concurrent execute calls with the same key run the block once`() {
        val key = "concurrent-${UUID.randomUUID()}"
        val executions = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val tasks =
                List(2) { index ->
                    Callable {
                        idempotencyService.execute<ConcurrentResult>(accountId, key, "concurrent_test") {
                            executions.incrementAndGet()
                            Thread.sleep(200)
                            ConcurrentResult(
                                token = "winner-$index",
                                id = UUID.randomUUID(),
                            )
                        }
                    }
                }

            val futures = executor.invokeAll(tasks)
            val results = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertThat(executions.get()).isEqualTo(1)
            assertThat(results[0]).isEqualTo(results[1])
            assertThat(idempotencyEntityRepository.countByAccountIdAndIdempotencyKey(accountId, key)).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `same key string is isolated per account`() {
        val key = "shared-${UUID.randomUUID()}"

        val first =
            idempotencyService.execute<CreateTransactionOutcome>(accountId, key, "create_transaction") {
                transactionService.createTransactionOutcome(
                    command = createTransactionCommand(financialAccountId, "Conta A"),
                    accountId = accountId,
                    userId = userId,
                )
            }
        val second =
            idempotencyService.execute<CreateTransactionOutcome>(secondAccountId, key, "create_transaction") {
                transactionService.createTransactionOutcome(
                    command = createTransactionCommand(secondFinancialAccountId, "Conta B"),
                    accountId = secondAccountId,
                    userId = userId,
                )
            }

        assertThat(first.transaction.id).isNotEqualTo(second.transaction.id)
        assertThat(idempotencyEntityRepository.countByAccountIdAndIdempotencyKey(accountId, key)).isEqualTo(1)
        assertThat(idempotencyEntityRepository.countByAccountIdAndIdempotencyKey(secondAccountId, key)).isEqualTo(1)
        assertThat(
            transactionService.findById(requireNotNull(first.transaction.id), accountId).description,
        ).isEqualTo("Conta A")
        assertThat(
            transactionService.findById(requireNotNull(second.transaction.id), secondAccountId).description,
        ).isEqualTo("Conta B")
    }

    private fun createBankAccount(
        scopedAccountId: UUID,
        scopedUserId: UUID,
        name: String,
    ): UUID =
        requireNotNull(
            financialAccountService.create(
                command =
                    CreateFinancialAccountCommand(
                        type = FinancialAccountType.BANK,
                        subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                        name = name,
                        issuerName = "Banco Teste",
                    ),
                accountId = scopedAccountId,
                userId = scopedUserId,
            ).id,
        )

    private fun createTransactionCommand(
        scopedFinancialAccountId: UUID,
        description: String,
    ): CreateTransactionCommand =
        CreateTransactionCommand(
            financialAccountId = scopedFinancialAccountId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            amount = BigDecimal("42.50"),
            date = LocalDate.of(2026, 7, 25),
            description = description,
        )

    private data class ConcurrentResult(
        val token: String,
        val id: UUID,
    )
}
