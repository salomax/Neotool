package io.github.salomax.neotool.banking.domain.import.dedup

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.model.transaction.TransactionEntity
import io.github.salomax.neotool.banking.repo.TransactionEntityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("CrossSourceDedupService")
class CrossSourceDedupServiceTest {

    private lateinit var transactionEntityRepository: TransactionEntityRepository
    private lateinit var service: CrossSourceDedupService

    private val financialAccountId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val today = LocalDate.now()
    private val amount = BigDecimal("99.50")

    @BeforeEach
    fun setUp() {
        transactionEntityRepository = mock()
        service = CrossSourceDedupService(transactionEntityRepository)
    }

    @Nested
    @DisplayName("findCandidates")
    inner class FindCandidatesTests {

        @Test
        fun `calls repository with parsed date amount and incomingSource name returns mapped domain list`() {
            val parsed = ParsedTransaction(
                creditDebitType = CreditDebitType.DEBIT,
                amount = amount,
                date = today,
                description = "Coffee",
            )
            val entity = TransactionEntity(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                belongsToUserId = userId,
                creditDebitType = CreditDebitType.DEBIT,
                type = TransactionType.OTHER_EXPENSE,
                status = TransactionStatus.POSTED,
                source = TransactionSource.MANUAL,
                amount = amount,
                currencyCode = "BRL",
                date = today,
                description = "Existing MANUAL",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
            whenever(
                transactionEntityRepository.findCrossSourceCandidates(
                    financialAccountId = eq(financialAccountId),
                    date = eq(today),
                    amount = eq(amount),
                    incomingSource = eq(TransactionSource.OPEN_FINANCE.name),
                ),
            ).thenReturn(listOf(entity))

            val result = service.findCandidates(
                financialAccountId = financialAccountId,
                parsed = parsed,
                incomingSource = TransactionSource.OPEN_FINANCE,
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(entity.id)
            assertThat(result[0].source).isEqualTo(TransactionSource.MANUAL)
            assertThat(result[0].amount).isEqualByComparingTo(amount)
            assertThat(result[0].date).isEqualTo(today)
            verify(transactionEntityRepository).findCrossSourceCandidates(
                financialAccountId = financialAccountId,
                date = today,
                amount = amount,
                incomingSource = "OPEN_FINANCE",
            )
        }

        @Test
        fun `returns empty list when repository returns empty`() {
            val parsed = ParsedTransaction(
                creditDebitType = CreditDebitType.DEBIT,
                amount = amount,
                date = today,
                description = "Only",
            )
            whenever(
                transactionEntityRepository.findCrossSourceCandidates(
                    financialAccountId = any(),
                    date = any(),
                    amount = any(),
                    incomingSource = any(),
                ),
            ).thenReturn(emptyList())

            val result = service.findCandidates(
                financialAccountId = financialAccountId,
                parsed = parsed,
                incomingSource = TransactionSource.PLUGGY,
            )

            assertThat(result).isEmpty()
        }
    }
}
