package io.github.salomax.neotool.banking.repo.attachment

import io.github.salomax.neotool.banking.model.attachment.TransactionAttachmentEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface TransactionAttachmentEntityRepository : JpaRepository<TransactionAttachmentEntity, UUID> {
    fun findByTransactionIdAndAccountId(transactionId: UUID, accountId: UUID): List<TransactionAttachmentEntity>
    fun findByTransactionIdInAndAccountId(transactionIds: Collection<UUID>, accountId: UUID): List<TransactionAttachmentEntity>
    fun findByIdAndAccountId(id: UUID, accountId: UUID): TransactionAttachmentEntity?
    fun countByTransactionIdAndAccountId(transactionId: UUID, accountId: UUID): Int
    fun deleteByIdAndAccountId(id: UUID, accountId: UUID): Int
}
