package io.github.salomax.neotool.assets.graphql.mapper

import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.graphql.dto.AssetDTO
import io.github.salomax.neotool.assets.graphql.dto.ConfirmAssetUploadInput
import io.github.salomax.neotool.assets.graphql.dto.CreateAssetUploadInput
import io.github.salomax.neotool.assets.storage.BucketResolver
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Mapper for converting between GraphQL inputs, DTOs, and domain entities for Assets.
 *
 * Separates mapping concerns from resolver logic for better testability and maintainability.
 * Follows the Mapper Pattern (v2.0.0) from docs/04-patterns/backend-patterns/mapper-pattern.md
 */
@Singleton
class AssetGraphQLMapper(
    private val bucketResolver: BucketResolver,
) {
    /**
     * Extract field with type safety and default values.
     *
     * @param input The input map from GraphQL
     * @param name The field name to extract
     * @param defaultValue Optional default value if field is missing or null
     * @return The extracted value or default, or throws if required field is missing
     */
    inline fun <reified T> extractField(
        input: Map<String, Any?>,
        name: String,
        defaultValue: T? = null,
    ): T {
        val value = input[name]
        if (value == null) {
            return defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
        }

        return if (value is T) {
            value
        } else {
            // Handle numeric type conversions (GraphQL Int may come as Int but we need Long)
            when {
                T::class == Long::class && value is Number -> {
                    @Suppress("UNCHECKED_CAST")
                    value.toLong() as T
                }

                T::class == Int::class && value is Number -> {
                    @Suppress("UNCHECKED_CAST")
                    value.toInt() as T
                }

                else -> {
                    defaultValue ?: throw IllegalArgumentException(
                        "Field '$name' has invalid type. Expected: ${T::class.simpleName}",
                    )
                }
            }
        }
    }

    /**
     * Parse and validate asset ID from string.
     *
     * @param id Asset ID as string
     * @return Parsed UUID
     * @throws IllegalArgumentException if ID format is invalid
     */
    fun toAssetId(id: String): UUID {
        return try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid asset ID format: $id", e)
        }
    }

    /**
     * Map GraphQL input map to CreateAssetUploadInput DTO.
     *
     * @param input The GraphQL input map
     * @return CreateAssetUploadInput with extracted and validated fields
     */
    fun mapToCreateAssetUploadInput(input: Map<String, Any?>): CreateAssetUploadInput {
        return CreateAssetUploadInput(
            namespace = extractField(input, "namespace"),
            filename = extractField(input, "filename"),
            mimeType = extractField(input, "mimeType"),
            sizeBytes = extractField(input, "sizeBytes"),
            idempotencyKey = input["idempotencyKey"] as? String,
        )
    }

    /**
     * Map GraphQL input map to ConfirmAssetUploadInput DTO.
     *
     * @param input The GraphQL input map
     * @return ConfirmAssetUploadInput with extracted and validated fields
     */
    fun mapToConfirmAssetUploadInput(input: Map<String, Any?>): ConfirmAssetUploadInput {
        val assetIdString: String = extractField(input, "assetId")
        val assetId = toAssetId(assetIdString)

        return ConfirmAssetUploadInput(
            assetId = assetId,
            checksum = input["checksum"] as? String,
        )
    }

    /**
     * Map Asset domain entity to AssetDTO.
     * Generates publicUrl dynamically from storageKey.
     *
     * @param asset The Asset domain entity
     * @return AssetDTO for GraphQL response
     */
    fun toAssetDTO(asset: Asset): AssetDTO {
        return AssetDTO.fromDomain(asset, bucketResolver.getPublicBaseUrl())
    }

    /**
     * Map list of Asset domain entities to list of AssetDTOs.
     * Generates publicUrl dynamically for each asset.
     *
     * @param assets List of Asset domain entities
     * @return List of AssetDTOs for GraphQL response
     */
    fun toAssetDTOs(assets: List<Asset>): List<AssetDTO> {
        val baseUrl = bucketResolver.getPublicBaseUrl()
        return assets.map { AssetDTO.fromDomain(it, baseUrl) }
    }
}
