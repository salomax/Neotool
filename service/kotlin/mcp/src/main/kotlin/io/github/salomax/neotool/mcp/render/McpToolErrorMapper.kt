package io.github.salomax.neotool.mcp.render

import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.mcp.protocol.McpToolExecutionException
import jakarta.inject.Singleton

@Singleton
class McpToolErrorMapper {
    fun toResult(exception: McpToolExecutionException): McpToolErrorResult =
        McpToolErrorResult(
            code = exception.code,
            service = exception.service,
            field = exception.field,
            parameters = exception.parameters.allowlisted(),
        )

    fun toResult(
        exception: DomainException,
        service: String,
    ): McpToolErrorResult =
        McpToolErrorResult(
            code = exception.errorCode.code,
            service = service,
            field = exception.field?.split(".")?.filter { it.isNotBlank() },
            parameters = exception.parameters.allowlisted(),
        )

    private fun Map<String, *>.allowlisted(): Map<String, Any?> =
        entries
            .filter { (key, _) -> key in ALLOWED_PARAMETER_KEYS }
            .associate { (key, value) -> key to value }

    companion object {
        private val ALLOWED_PARAMETER_KEYS =
            setOf(
                "actual",
                "actualLength",
                "field",
                "first",
                "limit",
                "max",
                "maxLength",
                "min",
                "month",
                "status",
                "year",
            )
    }
}
