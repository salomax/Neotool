package io.github.salomax.neotool.financialdata.model

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
@Table(name = "institution_data_restriction", schema = "financial_data")
open class InstitutionDataRestrictionEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "bacen_cod_inst", nullable = false, unique = true)
    open var bacenCodInst: String,
    @Column(nullable = false)
    open var reason: String,
    @Column(columnDefinition = "text")
    open var notes: String? = null,
    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = true,
    @Column(name = "created_by")
    open var createdBy: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
