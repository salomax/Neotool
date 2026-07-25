package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.common.provenance.ChangeOrigin
import io.github.salomax.neotool.common.provenance.OriginChannel
import java.util.UUID

data class TransactionChangeSnapshot(
    val categoryId: UUID?,
    val categorySource: CategorySource?,
    val merchantId: UUID?,
    val type: TransactionType,
    val description: String,
    val tags: List<String>,
    val origin: ChangeOrigin,
) {
    fun toMap(): Map<String, Any?> =
        mapOf(
            "categoryId" to categoryId?.toString(),
            "categorySource" to categorySource?.name,
            "merchantId" to merchantId?.toString(),
            "type" to type.name,
            "description" to description,
            "tags" to tags,
            "originChannel" to origin.channel.name,
            "originClientId" to origin.clientId,
            "originToolCallId" to origin.toolCallId?.toString(),
        )

    fun applyTo(transaction: Transaction): Transaction =
        transaction.copy(
            categoryId = categoryId,
            categorySource = categorySource,
            merchantId = merchantId,
            type = type,
            description = description,
            tags = tags,
            origin = origin,
        )

    companion object {
        fun from(transaction: Transaction): TransactionChangeSnapshot =
            TransactionChangeSnapshot(
                categoryId = transaction.categoryId,
                categorySource = transaction.categorySource,
                merchantId = transaction.merchantId,
                type = transaction.type,
                description = transaction.description,
                tags = transaction.tags,
                origin = transaction.origin,
            )

        fun fromMap(raw: Map<String, Any?>): TransactionChangeSnapshot =
            TransactionChangeSnapshot(
                categoryId = raw["categoryId"].toUuidOrNull(),
                categorySource =
                    raw["categorySource"]?.toString()?.takeIf { it.isNotBlank() }?.let(
                        CategorySource::valueOf,
                    ),
                merchantId = raw["merchantId"].toUuidOrNull(),
                type = TransactionType.valueOf(raw["type"]?.toString() ?: error("type is required")),
                description = raw["description"]?.toString() ?: error("description is required"),
                tags = (raw["tags"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                origin =
                    ChangeOrigin(
                        channel = OriginChannel.valueOf(raw["originChannel"]?.toString() ?: OriginChannel.UI.name),
                        clientId = raw["originClientId"]?.toString(),
                        toolCallId = raw["originToolCallId"].toUuidOrNull(),
                    ),
            )

        private fun Any?.toUuidOrNull(): UUID? = this?.toString()?.takeIf { it.isNotBlank() }?.let(UUID::fromString)
    }
}
