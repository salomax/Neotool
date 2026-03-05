package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.domain.enums.ModelStatus
import io.github.salomax.neotool.assistant.entity.LLMModelEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface LLMModelRepository : JpaRepository<LLMModelEntity, UUID> {
    @Query(
        value = """
        SELECT m.*
        FROM assistant.llm_models m
        JOIN assistant.llm_providers p ON p.id = m.provider_id
        WHERE m.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
        ORDER BY p.priority ASC, m.created_at ASC
        """,
        nativeQuery = true,
    )
    fun findActiveModelsForRouting(): List<LLMModelEntity>

    @Query(
        value = """
        SELECT m.*
        FROM assistant.llm_models m
        JOIN assistant.llm_providers p ON p.id = m.provider_id
        WHERE m.status = :status
          AND p.status = 'ACTIVE'
          AND :capability = ANY(m.capabilities)
        ORDER BY p.priority ASC, m.created_at ASC
        """,
        nativeQuery = true,
    )
    fun findByStatusAndCapabilitiesContaining(
        status: ModelStatus,
        capability: String,
    ): List<LLMModelEntity>

    @Query(
        value = """
        SELECT m.*
        FROM assistant.llm_models m
        JOIN assistant.llm_providers p ON p.id = m.provider_id
        WHERE m.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
        ORDER BY p.priority ASC, m.created_at ASC
        LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findPrimaryActiveModel(): LLMModelEntity?

    @Query(
        value = """
        SELECT m.*
        FROM assistant.llm_models m
        JOIN assistant.llm_providers p ON p.id = m.provider_id
        WHERE m.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
          AND m.model_name = :modelName
        ORDER BY p.priority ASC, m.created_at ASC
        LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findPrimaryActiveModelByName(modelName: String): LLMModelEntity?
}
