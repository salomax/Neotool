package io.github.salomax.neotool.banking.domain.attachment

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a file attachment linked to a banking record (Transaction or Holding).
 * The file itself is stored in the assets service under the "banking-attachments" namespace.
 */
data class TransactionAttachment(
    val id: UUID?,
    val accountId: UUID,
    val transactionId: UUID,
    val assetId: UUID,
    val createdAt: Instant = Instant.now(),
)

data class HoldingAttachment(
    val id: UUID?,
    val accountId: UUID,
    val holdingId: UUID,
    val assetId: UUID,
    val createdAt: Instant = Instant.now(),
)

/** Maximum number of files allowed per record. */
const val MAX_ATTACHMENTS_PER_RECORD = 3

/** Namespace used when calling the assets service for private file uploads. */
const val BANKING_ATTACHMENTS_NAMESPACE = "banking-attachments"
