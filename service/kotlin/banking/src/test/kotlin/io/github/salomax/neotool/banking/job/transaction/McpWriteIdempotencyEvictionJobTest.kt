package io.github.salomax.neotool.banking.job.transaction

import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRecord
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@DisplayName("McpWriteIdempotencyEvictionJob")
class McpWriteIdempotencyEvictionJobTest {
    @Test
    fun `eviction removes only rows past retention window`() {
        val now = Instant.parse("2026-07-25T12:00:00Z")
        val repository = InMemoryMcpWriteIdempotencyRepository()
        val accountId = UUID.randomUUID()
        val oldRecord =
            repository.insert(
                accountId = accountId,
                key = "old-key",
                createdAt = now.minus(Duration.ofHours(49)),
            )
        val freshRecord =
            repository.insert(
                accountId = accountId,
                key = "fresh-key",
                createdAt = now.minus(Duration.ofHours(2)),
            )

        val job =
            McpWriteIdempotencyEvictionJob(
                repository = repository,
                retention = Duration.ofHours(48),
                clock = { now },
            )

        job.run()

        assertThat(repository.findByAccountIdAndIdempotencyKey(accountId, oldRecord.idempotencyKey)).isNull()
        assertThat(repository.findByAccountIdAndIdempotencyKey(accountId, freshRecord.idempotencyKey)).isNotNull()
    }

    private class InMemoryMcpWriteIdempotencyRepository : McpWriteIdempotencyRepository {
        private val rows = ConcurrentHashMap<Pair<UUID, String>, McpWriteIdempotencyRecord>()

        fun insert(
            accountId: UUID,
            key: String,
            createdAt: Instant,
        ): McpWriteIdempotencyRecord {
            val record =
                McpWriteIdempotencyRecord(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    idempotencyKey = key,
                    operation = "create_transaction",
                    resultJson = """{"ok":true}""",
                    createdAt = createdAt,
                )
            rows[accountId to key] = record
            return record
        }

        override fun claim(
            accountId: UUID,
            idempotencyKey: String,
            operation: String,
        ): McpWriteIdempotencyRecord = error("Not used in this test")

        override fun findByAccountIdAndIdempotencyKey(
            accountId: UUID,
            idempotencyKey: String,
        ): McpWriteIdempotencyRecord? = rows[accountId to idempotencyKey]

        override fun storeResult(
            id: UUID,
            resultJson: String,
        ): McpWriteIdempotencyRecord = error("Not used in this test")

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
