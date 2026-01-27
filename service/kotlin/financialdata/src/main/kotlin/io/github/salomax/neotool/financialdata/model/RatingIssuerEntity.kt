package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.RatingIssuer
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
@Table(name = "rating_issuer", schema = "financial_data")
open class RatingIssuerEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(nullable = false, unique = true, length = 50)
    open var code: String,
    @Column(name = "display_name", nullable = false, length = 255)
    open var name: String,
    @Column(length = 500)
    open var website: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var config: Map<String, Any> = emptyMap(),
    @Column(name = "logo_asset_id", columnDefinition = "uuid")
    open var logoAssetId: UUID? = null,
    @Column(nullable = false)
    open var enabled: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    fun toDomain(): RatingIssuer {
        return RatingIssuer(
            id = this.id,
            code = this.code,
            name = this.name,
            website = this.website,
            config = this.config,
            logoAssetId = this.logoAssetId,
            enabled = this.enabled,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
