package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.entity.PromptEntity
import io.github.salomax.neotool.assistant.entity.PromptVersionEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface PromptRepository : JpaRepository<PromptEntity, UUID> {
    fun findByCategory(category: String): List<PromptEntity>

    @Query(
        value = """
        SELECT DISTINCT p.*
        FROM assistant.prompts p
        JOIN unnest(p.tags) AS tag ON TRUE
        WHERE tag IN (:tags)
        """,
        nativeQuery = true,
    )
    fun findByTags(tags: List<String>): List<PromptEntity>

    @Query(
        """
        SELECT pv FROM PromptVersionEntity pv
        WHERE pv.promptId = :promptId
          AND pv.status = 'ACTIVE'
        """,
    )
    fun findActiveVersion(promptId: UUID): PromptVersionEntity?
}
