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
@Table(name = "popular_institution", schema = "financial_data")
open class PopularInstitutionEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(nullable = false, unique = true)
    open var rank: Int = 0,
    @Column(name = "parent_cnpj", nullable = false)
    open var parentCnpj: String = "",
    @Column(name = "display_name", nullable = false, length = 100)
    open var displayName: String = "",
    @Column(name = "short_description", nullable = false, length = 200)
    open var shortDescription: String = "",
    @Column(nullable = false)
    open var active: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
