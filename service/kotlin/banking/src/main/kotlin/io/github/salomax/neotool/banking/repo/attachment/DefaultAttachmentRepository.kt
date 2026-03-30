package io.github.salomax.neotool.banking.repo.attachment

import io.github.salomax.neotool.banking.domain.attachment.HoldingAttachment
import io.github.salomax.neotool.banking.repo.attachment.HoldingAttachmentRepository
import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
import io.github.salomax.neotool.banking.repo.attachment.TransactionAttachmentRepository
import io.github.salomax.neotool.banking.model.attachment.HoldingAttachmentMapper
import io.github.salomax.neotool.banking.model.attachment.TransactionAttachmentMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultTransactionAttachmentRepository(
    private val entityRepository: TransactionAttachmentEntityRepository,
) : TransactionAttachmentRepository {

    override fun findByTransactionId(transactionId: UUID, accountId: UUID): List<TransactionAttachment> =
        entityRepository.findByTransactionIdAndAccountId(transactionId, accountId)
            .map(TransactionAttachmentMapper::toDomain)

    override fun findById(id: UUID, accountId: UUID): TransactionAttachment? =
        entityRepository.findByIdAndAccountId(id, accountId)
            ?.let(TransactionAttachmentMapper::toDomain)

    override fun countByTransactionId(transactionId: UUID, accountId: UUID): Int =
        entityRepository.countByTransactionIdAndAccountId(transactionId, accountId)

    @Transactional
    override fun save(attachment: TransactionAttachment): TransactionAttachment {
        val entity = TransactionAttachmentMapper.toEntity(attachment)
        return TransactionAttachmentMapper.toDomain(entityRepository.save(entity))
    }

    @Transactional
    override fun delete(id: UUID, accountId: UUID): Boolean =
        entityRepository.deleteByIdAndAccountId(id, accountId) > 0
}

@Singleton
open class DefaultHoldingAttachmentRepository(
    private val entityRepository: HoldingAttachmentEntityRepository,
) : HoldingAttachmentRepository {

    override fun findByHoldingId(holdingId: UUID, accountId: UUID): List<HoldingAttachment> =
        entityRepository.findByHoldingIdAndAccountId(holdingId, accountId)
            .map(HoldingAttachmentMapper::toDomain)

    override fun findById(id: UUID, accountId: UUID): HoldingAttachment? =
        entityRepository.findByIdAndAccountId(id, accountId)
            ?.let(HoldingAttachmentMapper::toDomain)

    override fun countByHoldingId(holdingId: UUID, accountId: UUID): Int =
        entityRepository.countByHoldingIdAndAccountId(holdingId, accountId)

    @Transactional
    override fun save(attachment: HoldingAttachment): HoldingAttachment {
        val entity = HoldingAttachmentMapper.toEntity(attachment)
        return HoldingAttachmentMapper.toDomain(entityRepository.save(entity))
    }

    @Transactional
    override fun delete(id: UUID, accountId: UUID): Boolean =
        entityRepository.deleteByIdAndAccountId(id, accountId) > 0
}
