package io.github.salomax.neotool.banking.model.issuer

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.util.UUID

/**
 * JPA entity for banking.issuers.
 * logo_key is null until IssuerLogoHydrationJob runs.
 */
@Entity
@Table(name = "issuers", schema = "banking")
open class IssuerEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(nullable = false, length = 100)
    open var name: String,
    @Column(length = 100)
    open var domain: String? = null,
    @Column(name = "compe_code", length = 3)
    open var compeCode: String? = null,
    @Column(name = "ispb", length = 8)
    open var ispb: String? = null,
    @Column(name = "logo_key")
    open var logoKey: String? = null,
    @Column(name = "country_code", length = 2, nullable = false)
    open var countryCode: String = "BR",
    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = true,
) : BaseEntity<UUID?>(id)
