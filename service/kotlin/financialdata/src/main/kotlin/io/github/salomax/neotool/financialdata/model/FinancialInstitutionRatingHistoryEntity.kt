package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.FinancialInstitutionRatingHistory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "financial_institution_rating_history", schema = "financial_data")
open class FinancialInstitutionRatingHistoryEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "financial_institution_id", nullable = false, columnDefinition = "uuid")
    open var financialInstitutionId: UUID,
    @Column(name = "issuer_id", nullable = false, columnDefinition = "uuid")
    open var issuerId: UUID,
    @Column(name = "rating_name", nullable = false, length = 255)
    open var name: String,
    @Column(name = "rating_label", nullable = false, length = 100)
    open var label: String,
    @Column(name = "rating_date", nullable = false)
    open var ratingDate: LocalDate,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_params", nullable = false, columnDefinition = "jsonb")
    open var customParams: Map<String, Any> = emptyMap(),
    @Column(name = "source_evidence_id", columnDefinition = "uuid")
    open var sourceEvidenceId: UUID? = null,
    @Column(name = "content_hash", nullable = false, length = 64)
    open var contentHash: String,
    @Column(name = "changed_at", nullable = false)
    open var changedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): FinancialInstitutionRatingHistory {
        return FinancialInstitutionRatingHistory(
            id = this.id,
            financialInstitutionId = this.financialInstitutionId,
            issuerId = this.issuerId,
            name = this.name,
            label = this.label,
            ratingDate = this.ratingDate,
            customParams = this.customParams,
            sourceEvidenceId = this.sourceEvidenceId,
            contentHash = this.contentHash,
            changedAt = this.changedAt,
        )
    }
}
