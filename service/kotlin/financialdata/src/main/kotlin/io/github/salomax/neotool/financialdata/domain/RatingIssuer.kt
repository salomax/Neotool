package io.github.salomax.neotool.financialdata.domain

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
)
