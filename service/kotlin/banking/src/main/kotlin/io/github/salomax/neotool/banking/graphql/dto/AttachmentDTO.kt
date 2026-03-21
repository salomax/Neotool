package io.github.salomax.neotool.banking.graphql.dto

import java.time.Instant
import java.util.UUID

/**
 * DTO returned by the GraphQL resolver for both TransactionAttachment and HoldingAttachment.
 * Asset metadata is resolved on-demand from the assets service.
 */
data class TransactionAttachmentDTO(
    val id: UUID,
    val transactionId: UUID,
    val assetId: UUID,
    val filename: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val downloadUrl: String?,
    val createdAt: Instant,
)

data class HoldingAttachmentDTO(
    val id: UUID,
    val holdingId: UUID,
    val assetId: UUID,
    val filename: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val downloadUrl: String?,
    val createdAt: Instant,
)
