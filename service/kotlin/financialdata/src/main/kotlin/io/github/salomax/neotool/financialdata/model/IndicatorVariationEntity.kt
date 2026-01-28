package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "indicator_variation", schema = "financial_data")
open class IndicatorVariationEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "indicator_id", nullable = false, columnDefinition = "uuid")
    open var indicatorId: UUID,
    @Column(nullable = false)
    open var label: String,
    @Column
    open var unit: String? = null,
    @Column(name = "is_default", nullable = false)
    open var isDefault: Boolean = false,
    @Column(name = "incremental_months")
    open var incrementalMonths: Int? = null,
    @Column(name = "incremental_years")
    open var incrementalYears: Int? = null,
    @Column(name = "incremental_days")
    open var incrementalDays: Int? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    fun toDomain(): IndicatorVariation {
        return IndicatorVariation(
            id = this.id,
            indicatorId = this.indicatorId,
            label = this.label,
            unit = this.unit,
            isDefault = this.isDefault,
            incrementalMonths = this.incrementalMonths,
            incrementalYears = this.incrementalYears,
            incrementalDays = this.incrementalDays,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
