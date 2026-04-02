package io.github.salomax.neotool.banking.model.import.nfce

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
@Table(name = "products", schema = "banking")
open class ProductEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "name", nullable = false)
    open var name: String,
    @Column(name = "ncm_code")
    open var ncmCode: String? = null,
    @Column(name = "default_unit")
    open var defaultUnit: String? = null,
    @Column(name = "category")
    open var category: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
