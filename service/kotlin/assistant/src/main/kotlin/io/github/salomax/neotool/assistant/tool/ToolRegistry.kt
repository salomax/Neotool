package io.github.salomax.neotool.assistant.tool

import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@TraceExecution
@Singleton
open class ToolRegistry {
    private val executors = ConcurrentHashMap<String, ToolExecutor>()

    open fun register(
        name: String,
        executor: ToolExecutor,
    ) {
        val toolName = name.trim()
        require(toolName.isNotEmpty()) { "Tool name is required" }
        executors[toolName] = executor
    }

    open suspend fun execute(
        name: String,
        accountId: UUID,
        input: JsonObject,
    ): Result<JsonObject> {
        val toolName = name.trim()
        val executor = executors[toolName] ?: return Result.failure(ToolNotFoundException(toolName))
        return try {
            executor.execute(accountId, input)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun registeredToolNames(): Set<String> = executors.keys.toSet()
}
