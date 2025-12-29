package io.github.salomax.neotool.assets.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * GraphQL input for creating asset upload.
 *
 * Maps to createAssetUpload mutation input.
 * ResourceType and resourceId are no longer required - namespace determines storage key template.
 */
@Introspected
@Serdeable
data class CreateAssetUploadInput(
    @field:NotBlank(message = "namespace must not be blank")
    val namespace: String,
    @field:NotBlank(message = "filename must not be blank")
    val filename: String,
    @field:NotBlank(message = "mimeType must not be blank")
    val mimeType: String,
    @field:Positive(message = "sizeBytes must be greater than 0")
    val sizeBytes: Long,
    val idempotencyKey: String? = null,
)
