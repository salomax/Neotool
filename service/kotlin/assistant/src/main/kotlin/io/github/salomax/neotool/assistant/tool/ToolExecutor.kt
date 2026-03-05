package io.github.salomax.neotool.assistant.tool

import java.util.UUID

typealias JsonObject = Map<String, Any?>

/**
 * Executes a domain tool within the account scope.
 */
fun interface ToolExecutor {
    suspend fun execute(
        accountId: UUID,
        input: JsonObject,
    ): Result<JsonObject>
}
