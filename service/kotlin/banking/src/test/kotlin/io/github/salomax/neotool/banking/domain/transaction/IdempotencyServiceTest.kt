package io.github.salomax.neotool.banking.domain.transaction

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyAlreadyClaimedException
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRecord
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRepository
import io.github.salomax.neotool.banking.service.transaction.IdempotencyService
import io.github.salomax.neotool.banking.service.transaction.execute
import io.github.salomax.neotool.common.error.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("IdempotencyService")
class IdempotencyServiceTest {
    private val repository = InMemoryMcpWriteIdempotencyRepository()

    /** Mirrors Micronaut's typical NON_EMPTY inclusion that omits empty collections. */
    private val objectMapper =
        ObjectMapper()
            .findAndRegisterModules()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
    private val service = IdempotencyService(repository, objectMapper)

    @Test
    fun `same account and key runs block once and replays stored result`() {
        val accountId = UUID.randomUUID()
        val executions = AtomicInteger(0)

        val first =
            service.execute<SampleResult>(accountId, "same-key", "create_transaction") {
                SampleResult(id = UUID.randomUUID(), marker = "first-${executions.incrementAndGet()}")
            }
        val second =
            service.execute<SampleResult>(accountId, "same-key", "create_transaction") {
                SampleResult(id = UUID.randomUUID(), marker = "second-${executions.incrementAndGet()}")
            }

        assertThat(executions.get()).isEqualTo(1)
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `replay round-trips result with empty collections under NON_EMPTY mapper`() {
        val accountId = UUID.randomUUID()
        val executions = AtomicInteger(0)

        val first =
            service.execute<SampleResultWithLists>(accountId, "empty-lists-key", "create_transaction") {
                SampleResultWithLists(
                    id = UUID.randomUUID(),
                    marker = "run-${executions.incrementAndGet()}",
                    tags = emptyList(),
                    appliedRuleIds = emptyList(),
                )
            }
        val second =
            service.execute<SampleResultWithLists>(accountId, "empty-lists-key", "create_transaction") {
                error("must not run on replay")
            }

        assertThat(executions.get()).isEqualTo(1)
        assertThat(second).isEqualTo(first)
        assertThat(second.tags).isEmpty()
        assertThat(second.appliedRuleIds).isEmpty()
        assertThat(repository.lastStoredJson).contains("\"tags\":[]")
        assertThat(repository.lastStoredJson).contains("\"appliedRuleIds\":[]")
    }

    @Test
    fun `different keys both execute`() {
        val accountId = UUID.randomUUID()
        val executions = AtomicInteger(0)

        val first =
            service.execute<SampleResult>(accountId, "key-a", "create_transaction") {
                SampleResult(id = UUID.randomUUID(), marker = "run-${executions.incrementAndGet()}")
            }
        val second =
            service.execute<SampleResult>(accountId, "key-b", "create_transaction") {
                SampleResult(id = UUID.randomUUID(), marker = "run-${executions.incrementAndGet()}")
            }

        assertThat(executions.get()).isEqualTo(2)
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `same key under different accounts is isolated`() {
        val firstAccountId = UUID.randomUUID()
        val secondAccountId = UUID.randomUUID()
        val executions = AtomicInteger(0)

        val first =
            service.execute<SampleResult>(firstAccountId, "shared-key", "create_transaction") {
                SampleResult(id = UUID.randomUUID(), marker = "run-${executions.incrementAndGet()}")
            }
        val second =
            service.execute<SampleResult>(secondAccountId, "shared-key", "create_transaction") {
                SampleResult(id = UUID.randomUUID(), marker = "run-${executions.incrementAndGet()}")
            }

        assertThat(executions.get()).isEqualTo(2)
        assertThat(first).isNotEqualTo(second)
        assertThat(
            service.execute<SampleResult>(
                firstAccountId,
                "shared-key",
                "create_transaction",
            ) { error("must not run") },
        ).isEqualTo(first)
        assertThat(
            service.execute<SampleResult>(
                secondAccountId,
                "shared-key",
                "create_transaction",
            ) { error("must not run") },
        ).isEqualTo(second)
    }

    @Test
    fun `missing key is rejected with clear domain error`() {
        val accountId = UUID.randomUUID()

        val error =
            assertThrows<ValidationException> {
                service.execute<SampleResult>(accountId, "   ", "create_transaction") {
                    SampleResult(id = UUID.randomUUID(), marker = "never")
                }
            }

        assertThat(error.errorCode).isEqualTo(BankingErrorCode.MCP_IDEMPOTENCY_KEY_REQUIRED)
        assertThat(error.field).isEqualTo("idempotencyKey")
    }

    private data class SampleResult(
        val id: UUID,
        val marker: String,
    )

    private data class SampleResultWithLists(
        val id: UUID,
        val marker: String,
        val tags: List<String>,
        val appliedRuleIds: List<UUID>,
    )

    private class InMemoryMcpWriteIdempotencyRepository : McpWriteIdempotencyRepository {
        private val rows = ConcurrentHashMap<Pair<UUID, String>, McpWriteIdempotencyRecord>()
        var lastStoredJson: String = "{}"
            private set

        override fun claim(
            accountId: UUID,
            idempotencyKey: String,
            operation: String,
        ): McpWriteIdempotencyRecord {
            val record =
                McpWriteIdempotencyRecord(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    idempotencyKey = idempotencyKey,
                    operation = operation,
                    resultJson = "{}",
                    createdAt = Instant.now(),
                )
            val previous = rows.putIfAbsent(accountId to idempotencyKey, record)
            if (previous != null) {
                throw McpWriteIdempotencyAlreadyClaimedException(accountId, idempotencyKey, operation)
            }
            return record
        }

        override fun findByAccountIdAndIdempotencyKey(
            accountId: UUID,
            idempotencyKey: String,
        ): McpWriteIdempotencyRecord? = rows[accountId to idempotencyKey]

        override fun storeResult(
            id: UUID,
            resultJson: String,
        ): McpWriteIdempotencyRecord {
            lastStoredJson = resultJson
            val existing =
                rows.values.first { it.id == id }
            val updated = existing.copy(resultJson = resultJson)
            rows[existing.accountId to existing.idempotencyKey] = updated
            return updated
        }

        override fun deleteCreatedBefore(cutoff: Instant): Int {
            val staleKeys =
                rows.entries
                    .filter { it.value.createdAt < cutoff }
                    .map { it.key }
            staleKeys.forEach(rows::remove)
            return staleKeys.size
        }
    }
}
