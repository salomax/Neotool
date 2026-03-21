package io.github.salomax.neotool.banking.domain.attachment

import java.util.UUID

interface TransactionAttachmentRepository {
    fun findByTransactionId(transactionId: UUID, accountId: UUID): List<TransactionAttachment>
    fun findById(id: UUID, accountId: UUID): TransactionAttachment?
    fun countByTransactionId(transactionId: UUID): Int
    fun save(attachment: TransactionAttachment): TransactionAttachment
    fun delete(id: UUID, accountId: UUID): Boolean
}

interface HoldingAttachmentRepository {
    fun findByHoldingId(holdingId: UUID, accountId: UUID): List<HoldingAttachment>
    fun findById(id: UUID, accountId: UUID): HoldingAttachment?
    fun countByHoldingId(holdingId: UUID): Int
    fun save(attachment: HoldingAttachment): HoldingAttachment
    fun delete(id: UUID, accountId: UUID): Boolean
}
