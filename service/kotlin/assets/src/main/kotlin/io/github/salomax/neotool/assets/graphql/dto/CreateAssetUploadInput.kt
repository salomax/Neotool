package io.github.salomax.neotool.assets.graphql.dto

import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * GraphQL input for creating asset upload.
 *
 * Maps to createAssetUpload mutation input.
 */
@Introspected
@Serdeable
data class CreateAssetUploadInput(
    @field:NotBlank(message = "namespace must not be blank")
    val namespace: String,
    @field:NotNull(message = "resourceType is required")
    val resourceType: AssetResourceType,
    @field:NotBlank(message = "resourceId must not be blank")
    val resourceId: String,
    @field:NotBlank(message = "filename must not be blank")
    val filename: String,
    @field:NotBlank(message = "mimeType must not be blank")
    val mimeType: String,
    @field:Positive(message = "sizeBytes must be greater than 0")
    val sizeBytes: Long,
    val idempotencyKey: String? = null,
)
