package io.github.salomax.neotool.assets.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * GraphQL input for confirming asset upload.
 *
 * Maps to confirmAssetUpload mutation input.
 */
@Introspected
@Serdeable
data class ConfirmAssetUploadInput(
    @field:NotNull(message = "assetId is required")
    val assetId: UUID,
    val checksum: String? = null,
)
