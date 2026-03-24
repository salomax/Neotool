package io.github.salomax.neotool.banking.repo.attachment

import io.github.salomax.neotool.banking.model.attachment.HoldingAttachmentEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface HoldingAttachmentEntityRepository : JpaRepository<HoldingAttachmentEntity, UUID> {
    fun findByHoldingIdAndAccountId(holdingId: UUID, accountId: UUID): List<HoldingAttachmentEntity>
    fun findByIdAndAccountId(id: UUID, accountId: UUID): HoldingAttachmentEntity?
    fun countByHoldingIdAndAccountId(holdingId: UUID, accountId: UUID): Int
    fun deleteByIdAndAccountId(id: UUID, accountId: UUID): Int
}
