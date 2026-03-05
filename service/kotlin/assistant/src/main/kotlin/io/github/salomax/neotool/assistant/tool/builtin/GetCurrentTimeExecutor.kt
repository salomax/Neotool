package io.github.salomax.neotool.assistant.tool.builtin

import io.github.salomax.neotool.assistant.tool.ToolExecutor
import io.github.salomax.neotool.assistant.tool.ToolRegistry
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@TraceExecution
@Singleton
open class GetCurrentTimeExecutor : ToolExecutor {
    override suspend fun execute(
        accountId: UUID,
        input: Map<String, Any?>,
    ): Result<Map<String, Any?>> {
        return Result.success(
            mapOf(
                "datetime" to Instant.now().toString(),
                "timezone" to "UTC",
            ),
        )
    }

    companion object {
        const val TOOL_NAME = "get_current_time"
    }
}

@Singleton
class BuiltinToolRegistrar(
    private val toolRegistry: ToolRegistry,
    private val getCurrentTimeExecutor: GetCurrentTimeExecutor,
) {
    @PostConstruct
    fun registerBuiltins() {
        toolRegistry.register(GetCurrentTimeExecutor.TOOL_NAME, getCurrentTimeExecutor)
    }
}
