package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.Indicator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "indicator", schema = "financial_data")
open class IndicatorEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "country_code", nullable = false, length = 2)
    open var countryCode: String,
    @Column(nullable = false)
    open var label: String,
    @Column(nullable = false)
    open var issuer: String,
    @Column
    open var description: String? = null,
    @Column(nullable = false)
    open var frequency: String,
    @Column(nullable = false)
    open var unit: String,
    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    fun toDomain(): Indicator {
        return Indicator(
            id = this.id,
            countryCode = this.countryCode,
            label = this.label,
            issuer = this.issuer,
            description = this.description,
            frequency = this.frequency,
            unit = this.unit,
            isActive = this.isActive,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
