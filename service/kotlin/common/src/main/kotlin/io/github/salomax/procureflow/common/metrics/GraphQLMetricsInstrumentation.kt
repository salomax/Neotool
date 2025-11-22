package io.github.salomax.neotool.common.metrics

import graphql.ExecutionResult
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

/**
 * GraphQL Metrics Instrumentation
 *
 * Automatically tracks metrics for all GraphQL operations:
 * - Operation name and type (query/mutation/subscription)
 * - Operation duration (latency)
 * - Success/error counts
 */
abstract class GraphQLMetricsInstrumentation(
    protected val meterRegistry: MeterRegistry,
) : SimplePerformantInstrumentation() {
    /**
     * Get the module name for tagging metrics (e.g., "app", "assistant", "security")
     */
    protected abstract fun getModuleName(): String

    override fun beginExecution(
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult> {
        val operationInfo = extractOperationInfo(parameters)
        val startTime = System.nanoTime()

        return object : InstrumentationContext<ExecutionResult> {
            override fun onDispatched() {
                // Track operation count
                Counter.builder("graphql.operation.count")
                    .tag("module", getModuleName())
                    .tag("operation", operationInfo.name)
                    .tag("type", operationInfo.type)
                    .description("Total count of GraphQL operations")
                    .register(meterRegistry)
                    .increment()
            }

            override fun onCompleted(
                result: ExecutionResult,
                throwable: Throwable?,
            ) {
                val duration = System.nanoTime() - startTime

                // Track duration
                Timer.builder("graphql.operation.duration")
                    .tag("module", getModuleName())
                    .tag("operation", operationInfo.name)
                    .tag("type", operationInfo.type)
                    .description("Duration of GraphQL operations")
                    .register(meterRegistry)
                    .record(duration, TimeUnit.NANOSECONDS)

                // Track success/error
                val hasError = throwable != null || result.errors.isNotEmpty()
                Counter.builder("graphql.operation.${if (hasError) "error" else "success"}.count")
                    .tag("module", getModuleName())
                    .tag("operation", operationInfo.name)
                    .tag("type", operationInfo.type)
                    .description("Count of ${if (hasError) "failed" else "successful"} GraphQL operations")
                    .register(meterRegistry)
                    .increment()
            }
        }
    }

    /**
     * Extract operation name and type from execution parameters
     */
    private fun extractOperationInfo(parameters: InstrumentationExecutionParameters): OperationInfo {
        val query = parameters.query ?: return OperationInfo("anonymous", "unknown")

        // Extract operation type and name from query string
        // Pattern: "query OperationName" or "mutation OperationName" or "subscription OperationName"
        val operationMatch =
            Regex("""(query|mutation|subscription)\s+(\w+)""", RegexOption.IGNORE_CASE)
                .find(query)

        if (operationMatch != null) {
            val type = operationMatch.groupValues[1].lowercase()
            val name = operationMatch.groupValues[2]
            return OperationInfo(name, type)
        }

        // Fallback: try to detect type without name
        val typeMatch =
            Regex("""^\s*(query|mutation|subscription)\s""", RegexOption.IGNORE_CASE)
                .find(query)

        val type = typeMatch?.groupValues?.get(1)?.lowercase() ?: "query"
        return OperationInfo("anonymous", type)
    }

    private data class OperationInfo(
        val name: String,
        val type: String,
    )
}
