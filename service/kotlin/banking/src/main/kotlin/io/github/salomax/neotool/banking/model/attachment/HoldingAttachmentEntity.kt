package io.github.salomax.neotool.banking.model.attachment

import io.github.salomax.neotool.banking.domain.attachment.HoldingAttachment
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "holding_attachments", schema = "banking")
open class HoldingAttachmentEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,

    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,

    @Column(name = "holding_id", nullable = false)
    open var holdingId: UUID,

    @Column(name = "asset_id", nullable = false)
    open var assetId: UUID,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)

object HoldingAttachmentMapper {
    fun toDomain(entity: HoldingAttachmentEntity) = HoldingAttachment(
        id = entity.id,
        accountId = entity.accountId,
        holdingId = entity.holdingId,
        assetId = entity.assetId,
        createdAt = entity.createdAt,
    )

    fun toEntity(domain: HoldingAttachment) = HoldingAttachmentEntity(
        accountId = domain.accountId,
        holdingId = domain.holdingId,
        assetId = domain.assetId,
        createdAt = domain.createdAt,
    )
}
