package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.McpWriteIdempotencyEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface McpWriteIdempotencyEntityRepository : JpaRepository<McpWriteIdempotencyEntity, UUID> {
    fun findByAccountIdAndIdempotencyKey(
        accountId: UUID,
        idempotencyKey: String,
    ): McpWriteIdempotencyEntity?

    fun countByAccountIdAndIdempotencyKey(
        accountId: UUID,
        idempotencyKey: String,
    ): Long

    @Query(
        """
        DELETE FROM banking.mcp_write_idempotency
         WHERE created_at < :cutoff
        """,
        nativeQuery = true,
    )
    fun deleteCreatedBefore(cutoff: Instant): Int
}
