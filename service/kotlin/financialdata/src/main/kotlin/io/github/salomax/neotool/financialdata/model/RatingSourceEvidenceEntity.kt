package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.RatingSourceEvidence
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rating_source_evidence", schema = "financial_data")
open class RatingSourceEvidenceEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "issuer_id", nullable = false, columnDefinition = "uuid")
    open var issuerId: UUID,
    @Column(name = "fetched_at", nullable = false)
    open var fetchedAt: Instant = Instant.now(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var metadata: Map<String, Any> = emptyMap(),
    @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
    open var rawPayload: String,
    @Column(name = "content_hash", nullable = false, length = 64)
    open var contentHash: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    fun toDomain(): RatingSourceEvidence {
        return RatingSourceEvidence(
            id = this.id,
            issuerId = this.issuerId,
            fetchedAt = this.fetchedAt,
            metadata = this.metadata,
            rawPayload = this.rawPayload,
            contentHash = this.contentHash,
            createdAt = this.createdAt,
        )
    }
}
