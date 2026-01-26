package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.FinancialInstitutionRatingIssuer
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
import java.util.UUID

@Entity
@Table(name = "financial_institution_rating_issuer", schema = "financial_data")
open class FinancialInstitutionRatingIssuerEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "rating_issuer_id", nullable = false, columnDefinition = "uuid")
    open var ratingIssuerId: UUID,
    @Column(name = "financial_institution_id", nullable = false, columnDefinition = "uuid")
    open var financialInstitutionId: UUID,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var config: Map<String, Any> = emptyMap(),
    @Column(nullable = false)
    open var enabled: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): FinancialInstitutionRatingIssuer {
        return FinancialInstitutionRatingIssuer(
            id = this.id,
            ratingIssuerId = this.ratingIssuerId,
            financialInstitutionId = this.financialInstitutionId,
            config = this.config,
            enabled = this.enabled,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
