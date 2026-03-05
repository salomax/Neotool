package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.entity.CompletionRequestEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface CompletionRequestRepository : JpaRepository<CompletionRequestEntity, UUID> {
    @Query(
        """
        SELECT c FROM CompletionRequestEntity c
        WHERE c.accountId = :accountId
        ORDER BY c.createdAt DESC
        """,
    )
    fun findByAccountIdAndCreatedAtDesc(accountId: UUID): List<CompletionRequestEntity>

    fun findByRequestedByService(requestedByService: String): List<CompletionRequestEntity>
}
