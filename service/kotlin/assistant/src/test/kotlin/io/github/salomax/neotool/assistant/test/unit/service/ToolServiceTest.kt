package io.github.salomax.neotool.assistant.test.unit.service

import io.github.salomax.neotool.assistant.domain.enums.ToolExecutionStatus
import io.github.salomax.neotool.assistant.domain.enums.ToolStatus
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.assistant.entity.ToolExecutionEntity
import io.github.salomax.neotool.assistant.repository.ToolExecutionRepository
import io.github.salomax.neotool.assistant.repository.ToolRepository
import io.github.salomax.neotool.assistant.service.RegisterToolInput
import io.github.salomax.neotool.assistant.service.ToolService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ToolServiceTest {
    private lateinit var toolRepository: ToolRepository
    private lateinit var toolExecutionRepository: ToolExecutionRepository
    private lateinit var service: ToolService

    @BeforeEach
    fun setUp() {
        toolRepository = mock()
        toolExecutionRepository = mock()
        service = ToolService(toolRepository, toolExecutionRepository)
    }

    @Test
    fun `registerTool persists metadata to tools table`() {
        val toolId = UUID.randomUUID()
        whenever(toolRepository.save(any())).thenAnswer { invocation ->
            val tool = invocation.arguments[0] as ToolEntity
            ToolEntity(
                id = toolId,
                name = tool.name,
                description = tool.description,
                category = tool.category,
                inputSchema = tool.inputSchema,
                outputSchema = tool.outputSchema,
                executorService = tool.executorService,
                executorEndpoint = tool.executorEndpoint,
                timeoutMs = tool.timeoutMs,
                retryPolicy = tool.retryPolicy,
                status = tool.status,
                createdAt = tool.createdAt,
                updatedAt = tool.updatedAt,
            )
        }

        val result =
            service.registerTool(
                RegisterToolInput(
                    name = "get_account_balance",
                    description = "Get current account balance",
                    category = "accounts",
                    inputSchema = mapOf("type" to "object"),
                    outputSchema = mapOf("type" to "object"),
                    executorService = "app-service",
                    executorEndpoint = "/internal/tools/get-balance",
                    timeoutMs = 3000,
                    retryPolicy = mapOf("maxAttempts" to 3),
                    status = ToolStatus.ACTIVE,
                ),
            )

        assertThat(result.id).isEqualTo(toolId)
        assertThat(result.name).isEqualTo("get_account_balance")

        val captor = argumentCaptor<ToolEntity>()
        verify(toolRepository).save(captor.capture())
        assertThat(captor.firstValue.executorService).isEqualTo("app-service")
        assertThat(captor.firstValue.timeoutMs).isEqualTo(3000)
    }

    @Test
    fun `getAvailableTools returns ACTIVE tools`() {
        whenever(toolRepository.findByStatus(ToolStatus.ACTIVE)).thenReturn(
            listOf(
                ToolEntity(
                    id = UUID.randomUUID(),
                    name = "tool-1",
                    description = "d",
                    category = "c",
                    executorService = "svc",
                    executorEndpoint = "/tool-1",
                    timeoutMs = 1000,
                    status = ToolStatus.ACTIVE,
                ),
            ),
        )

        val tools = service.getAvailableTools()
        assertThat(tools).hasSize(1)
        assertThat(tools.first().status).isEqualTo(ToolStatus.ACTIVE)
    }

    @Test
    fun `recordExecution writes tool_executions row`() {
        val executionId = UUID.randomUUID()
        whenever(toolExecutionRepository.save(any())).thenAnswer { invocation ->
            val row = invocation.arguments[0] as ToolExecutionEntity
            ToolExecutionEntity(
                id = executionId,
                completionRequestId = row.completionRequestId,
                toolId = row.toolId,
                input = row.input,
                output = row.output,
                status = row.status,
                error = row.error,
                latencyMs = row.latencyMs,
                executedAt = row.executedAt,
            )
        }

        val requestId = UUID.randomUUID()
        val toolId = UUID.randomUUID()
        val saved =
            service.recordExecution(
                completionRequestId = requestId,
                toolId = toolId,
                input = mapOf("accountId" to requestId.toString()),
                output = mapOf("balance" to 90),
                status = ToolExecutionStatus.SUCCESS,
                latencyMs = 42,
            )

        assertThat(saved.id).isEqualTo(executionId)
        assertThat(saved.status).isEqualTo(ToolExecutionStatus.SUCCESS)

        val captor = argumentCaptor<ToolExecutionEntity>()
        verify(toolExecutionRepository).save(captor.capture())
        assertThat(captor.firstValue.completionRequestId).isEqualTo(requestId)
        assertThat(captor.firstValue.toolId).isEqualTo(toolId)
        assertThat(captor.firstValue.latencyMs).isEqualTo(42)
    }
}
