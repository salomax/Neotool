package io.github.salomax.neotool.banking.repo.attachment

import io.github.salomax.neotool.banking.domain.attachment.HoldingAttachment
import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
import java.util.UUID

interface TransactionAttachmentRepository {
    fun findByTransactionId(
        transactionId: UUID,
        accountId: UUID,
    ): List<TransactionAttachment>

    fun findByTransactionIds(
        transactionIds: Collection<UUID>,
        accountId: UUID,
    ): Map<UUID, List<TransactionAttachment>>

    fun findById(
        id: UUID,
        accountId: UUID,
    ): TransactionAttachment?

    fun countByTransactionId(
        transactionId: UUID,
        accountId: UUID,
    ): Int

    fun save(attachment: TransactionAttachment): TransactionAttachment

    fun delete(
        id: UUID,
        accountId: UUID,
    ): Boolean
}

interface HoldingAttachmentRepository {
    fun findByHoldingId(
        holdingId: UUID,
        accountId: UUID,
    ): List<HoldingAttachment>

    fun findById(
        id: UUID,
        accountId: UUID,
    ): HoldingAttachment?

    fun countByHoldingId(
        holdingId: UUID,
        accountId: UUID,
    ): Int

    fun save(attachment: HoldingAttachment): HoldingAttachment

    fun delete(
        id: UUID,
        accountId: UUID,
    ): Boolean

    /** Removes all attachments for the holding; returns number of rows deleted. */
    fun deleteByHoldingIdAndAccountId(
        holdingId: UUID,
        accountId: UUID,
    ): Int
}
