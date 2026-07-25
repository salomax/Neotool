package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.model.dto.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.repo.transaction.McpChangeLogRepository
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.transaction.McpChangeLogService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.provenance.ChangeOrigin
import io.github.salomax.neotool.common.provenance.OriginChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("McpChangeLogService")
class McpChangeLogServiceTest {
    private lateinit var repository: McpChangeLogRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository
    private lateinit var service: McpChangeLogService

    private val accountId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val toolCallId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        repository = mock()
        transactionRepository = mock()
        snapshotRepository = mock()
        service = McpChangeLogService(repository, transactionRepository, snapshotRepository)
    }

    @Test
    fun `listForAccount requires at least one filter`() {
        val ex =
            assertThrows<ValidationException> {
                service.listForAccount(
                    accountId = accountId,
                    clientId = null,
                    tokenId = null,
                    toolCallId = null,
                    limit = 50,
                    offset = 0,
                )
            }

        assertThat(ex.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT)
        assertThat(ex.field).isEqualTo("filters")
    }

    @Test
    fun `revertToolCallChanges restores unchanged rows and writes REVERT audit entries`() {
        val transactionId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-07-24T10:15:30Z")
        val original =
            transaction(
                id = transactionId,
                categoryId = null,
                categorySource = null,
                origin = ChangeOrigin.ui(),
                updatedAt = createdAt.minusSeconds(60),
            )
        val current =
            original.copy(
                categoryId = UUID.randomUUID(),
                categorySource = CategorySource.MANUAL,
                origin = ChangeOrigin.mcp("mcp-client", toolCallId),
                updatedAt = createdAt,
            )
        val updateEntry =
            McpChangeLogEntry(
                toolCallId = toolCallId,
                clientId = "mcp-client",
                tokenId = "origin-token",
                resourceType = McpChangeLogResourceType.TRANSACTION,
                resourceId = transactionId,
                action = McpChangeLogAction.UPDATE,
                before = TransactionChangeSnapshot.from(original).toMap(),
                after = TransactionChangeSnapshot.from(current).toMap(),
                createdAt = createdAt,
            )
        whenever(repository.findOriginalUpdatesForAccount(accountId, toolCallId)).thenReturn(listOf(updateEntry))
        whenever(transactionRepository.findByIds(listOf(transactionId), accountId)).thenReturn(listOf(current))
        whenever(transactionRepository.update(any())).thenAnswer { it.getArgument(0) }

        val result = service.revertToolCallChanges(accountId, toolCallId)

        assertThat(result).isEqualTo(BulkCategorizeResult(affectedCount = 1, errors = emptyList()))
        val revertedCaptor = argumentCaptor<Transaction>()
        verify(transactionRepository).update(revertedCaptor.capture())
        assertThat(revertedCaptor.firstValue.categoryId).isNull()
        assertThat(revertedCaptor.firstValue.categorySource).isNull()
        assertThat(revertedCaptor.firstValue.origin.channel).isEqualTo(OriginChannel.UI)

        val entriesCaptor = argumentCaptor<List<McpChangeLogEntry>>()
        verify(repository).saveAll(entriesCaptor.capture())
        assertThat(entriesCaptor.firstValue).hasSize(1)
        val revertEntry = entriesCaptor.firstValue.single()
        assertThat(revertEntry.action).isEqualTo(McpChangeLogAction.REVERT)
        assertThat(revertEntry.before["categoryId"]).isEqualTo(current.categoryId.toString())
        assertThat(revertEntry.after["categoryId"]).isNull()
        assertThat(revertEntry.after["originChannel"]).isEqualTo(OriginChannel.UI.name)
        verify(snapshotRepository).markDirtyFromMonth(financialAccountId, 2026, 7)
    }

    @Test
    fun `revertToolCallChanges skips rows changed after original tool call`() {
        val transactionId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-07-24T10:15:30Z")
        val original = transaction(id = transactionId, updatedAt = createdAt.minusSeconds(60))
        val current = original.copy(updatedAt = createdAt.plusSeconds(1))
        val updateEntry =
            McpChangeLogEntry(
                toolCallId = toolCallId,
                clientId = "mcp-client",
                tokenId = "origin-token",
                resourceType = McpChangeLogResourceType.TRANSACTION,
                resourceId = transactionId,
                action = McpChangeLogAction.UPDATE,
                before = TransactionChangeSnapshot.from(original).toMap(),
                after = TransactionChangeSnapshot.from(original.copy(categoryId = UUID.randomUUID())).toMap(),
                createdAt = createdAt,
            )
        whenever(repository.findOriginalUpdatesForAccount(accountId, toolCallId)).thenReturn(listOf(updateEntry))
        whenever(transactionRepository.findByIds(listOf(transactionId), accountId)).thenReturn(listOf(current))

        val result = service.revertToolCallChanges(accountId, toolCallId)

        assertThat(result.affectedCount).isZero()
        assertThat(result.errors).singleElement().asString().contains(transactionId.toString())
        verify(transactionRepository, never()).update(any())
        verify(repository, never()).saveAll(any())
        verify(snapshotRepository, never()).markDirtyFromMonth(any(), any(), any())
    }

    private fun transaction(
        id: UUID = UUID.randomUUID(),
        categoryId: UUID? = null,
        categorySource: CategorySource? = null,
        origin: ChangeOrigin = ChangeOrigin.ui(),
        updatedAt: Instant = Instant.parse("2026-07-24T10:00:00Z"),
    ) = Transaction(
        id = id,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = UUID.randomUUID(),
        creditDebitType = CreditDebitType.DEBIT,
        type = TransactionType.OTHER_EXPENSE,
        source = TransactionSource.MANUAL,
        amount = BigDecimal("10.00"),
        date = LocalDate.of(2026, 7, 24),
        description = "Sample",
        categoryId = categoryId,
        categorySource = categorySource,
        origin = origin,
        createdAt = updatedAt.minusSeconds(300),
        updatedAt = updatedAt,
    )
}
