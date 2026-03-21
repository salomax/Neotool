package io.github.salomax.neotool.banking.model.attachment

import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
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
@Table(name = "transaction_attachments", schema = "banking")
open class TransactionAttachmentEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,

    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,

    @Column(name = "transaction_id", nullable = false)
    open var transactionId: UUID,

    @Column(name = "asset_id", nullable = false)
    open var assetId: UUID,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)

object TransactionAttachmentMapper {
    fun toDomain(entity: TransactionAttachmentEntity) = TransactionAttachment(
        id = entity.id,
        accountId = entity.accountId,
        transactionId = entity.transactionId,
        assetId = entity.assetId,
        createdAt = entity.createdAt,
    )

    fun toEntity(domain: TransactionAttachment) = TransactionAttachmentEntity(
        accountId = domain.accountId,
        transactionId = domain.transactionId,
        assetId = domain.assetId,
        createdAt = domain.createdAt,
    )
}
