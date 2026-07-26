package io.github.salomax.neotool.banking.service.transaction

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Singleton

@ConfigurationProperties("banking.mcp-write-bulk")
data class McpWriteBulkConfiguration(
    val maxAffectedRecords: Int = 50,
)

sealed interface BulkWriteCapDecision {
    data object Proceed : BulkWriteCapDecision

    data class ConfirmationRequired(
        val affectedCount: Int,
    ) : BulkWriteCapDecision
}

@Singleton
class BulkWriteCapGuard(
    private val config: McpWriteBulkConfiguration,
) {
    fun evaluate(affectedCount: Int): BulkWriteCapDecision {
        require(affectedCount >= 0) { "affectedCount must be zero or greater" }
        return if (affectedCount > config.maxAffectedRecords) {
            BulkWriteCapDecision.ConfirmationRequired(affectedCount)
        } else {
            BulkWriteCapDecision.Proceed
        }
    }
}
