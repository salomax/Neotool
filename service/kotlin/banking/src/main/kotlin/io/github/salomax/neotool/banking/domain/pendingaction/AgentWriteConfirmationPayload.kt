package io.github.salomax.neotool.banking.domain.pendingaction

import java.nio.charset.StandardCharsets
import java.util.UUID

private const val OP_DELETE_TRANSACTION = "delete_transaction"
private const val OP_BULK_CATEGORIZE = "bulk_categorize"
private const val OP_APPLY_RULE_TO_HISTORICAL = "apply_rule_to_historical"

sealed interface AgentWriteConfirmationPayload {
    val operation: String
    val origin: AgentWriteOrigin

    /** Stable payload representation stored in `payloadJson`. */
    fun toPayloadMap(): Map<String, Any?>
}

data class AgentWriteOrigin(
    val clientId: String,
    val toolCallId: UUID,
    val tokenId: String,
) {
    fun toPayloadMap(): Map<String, Any?> =
        mapOf(
            "client_id" to clientId,
            "tool_call_id" to toolCallId.toString(),
            "token_id" to tokenId,
        )
}

data class DeleteTransactionConfirmationPayload(
    val transactionId: UUID,
    override val origin: AgentWriteOrigin,
) : AgentWriteConfirmationPayload {
    override val operation: String = OP_DELETE_TRANSACTION

    override fun toPayloadMap(): Map<String, Any?> =
        mapOf(
            "operation" to operation,
            "request_id" to transactionId.toString(),
            "transaction_id" to transactionId.toString(),
            "origin" to origin.toPayloadMap(),
        )
}

data class BulkCategorizeConfirmationPayload(
    val transactionIds: List<UUID>,
    val categoryId: UUID,
    override val origin: AgentWriteOrigin,
) : AgentWriteConfirmationPayload {
    override val operation: String = OP_BULK_CATEGORIZE

    private val requestId: UUID = bulkCategorizeRequestId(transactionIds = transactionIds, categoryId = categoryId)

    override fun toPayloadMap(): Map<String, Any?> =
        mapOf(
            "operation" to operation,
            "request_id" to requestId.toString(),
            "transaction_ids" to transactionIds.map(UUID::toString),
            "category_id" to categoryId.toString(),
            "origin" to origin.toPayloadMap(),
        )
}

fun bulkCategorizeRequestId(
    transactionIds: List<UUID>,
    categoryId: UUID,
): UUID =
    UUID.nameUUIDFromBytes(
        buildString {
            append(OP_BULK_CATEGORIZE)
            append('|')
            append(categoryId)
            append('|')
            transactionIds.sorted().joinTo(this, separator = ",")
        }.toByteArray(StandardCharsets.UTF_8),
    )

data class ApplyRuleToHistoricalConfirmationPayload(
    val ruleId: UUID,
    override val origin: AgentWriteOrigin,
) : AgentWriteConfirmationPayload {
    override val operation: String = OP_APPLY_RULE_TO_HISTORICAL

    override fun toPayloadMap(): Map<String, Any?> =
        mapOf(
            "operation" to operation,
            "request_id" to ruleId.toString(),
            "rule_id" to ruleId.toString(),
            "origin" to origin.toPayloadMap(),
        )
}

object AgentWriteConfirmationPayloads {
    fun fromMap(payload: Map<String, Any?>): AgentWriteConfirmationPayload? {
        val operation = payload["operation"] as? String ?: return null
        val origin = payload["origin"].toAgentWriteOrigin() ?: return null
        return when (operation) {
            OP_DELETE_TRANSACTION ->
                DeleteTransactionConfirmationPayload(
                    transactionId =
                        UUID.fromString(
                            payload.stringValue("transaction_id")
                                ?: payload.stringValue("request_id")
                                ?: return null,
                        ),
                    origin = origin,
                )
            OP_BULK_CATEGORIZE ->
                BulkCategorizeConfirmationPayload(
                    transactionIds = payload.uuidListValue("transaction_ids") ?: return null,
                    categoryId = UUID.fromString(payload.stringValue("category_id") ?: return null),
                    origin = origin,
                )
            OP_APPLY_RULE_TO_HISTORICAL ->
                ApplyRuleToHistoricalConfirmationPayload(
                    ruleId =
                        UUID.fromString(
                            payload.stringValue("rule_id")
                                ?: payload.stringValue("request_id")
                                ?: return null,
                        ),
                    origin = origin,
                )
            else -> null
        }
    }

    private fun Map<String, Any?>.stringValue(key: String): String? = this[key] as? String

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.uuidListValue(key: String): List<UUID>? {
        val raw = this[key] as? List<*> ?: return null
        return raw.mapNotNull {
            when (it) {
                is UUID -> it
                is String -> UUID.fromString(it)
                else -> null
            }
        }.takeIf { it.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.toAgentWriteOrigin(): AgentWriteOrigin? {
        val raw = this as? Map<String, Any?> ?: return null
        val clientId = raw["client_id"] as? String ?: return null
        val toolCallId = raw["tool_call_id"] as? String ?: return null
        val tokenId = raw["token_id"] as? String ?: return null
        return AgentWriteOrigin(
            clientId = clientId,
            toolCallId = UUID.fromString(toolCallId),
            tokenId = tokenId,
        )
    }
}
