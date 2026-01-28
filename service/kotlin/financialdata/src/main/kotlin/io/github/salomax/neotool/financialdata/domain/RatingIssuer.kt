package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.RatingIssuerEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

@Serdeable
data class RatingIssuer(
    val id: UUID? = null,
    val code: String,
    val name: String,
    val website: String? = null,
    val config: Map<String, Any> = emptyMap(),
    val logoAssetId: UUID? = null,
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): RatingIssuerEntity {
        return RatingIssuerEntity(
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
