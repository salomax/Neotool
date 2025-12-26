package io.github.salomax.neotool.assets.graphql.dto

import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

/**
 * GraphQL DTO for Asset.
 *
 * Maps domain Asset to GraphQL schema type.
 * Follows project standards for DTOs (no tests needed for data classes).
 */
@Introspected
@Serdeable
data class AssetDTO(
    val id: UUID?,
    val ownerId: String,
    val namespace: String,
    val resourceType: AssetResourceType,
    val resourceId: String,
    val storageKey: String,
    val storageRegion: String,
    val storageBucket: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val checksum: String?,
    val originalFilename: String?,
    val uploadUrl: String?,
    val uploadExpiresAt: Instant?,
    val publicUrl: String, // Generated dynamically from storageKey
    val status: AssetStatus,
    val idempotencyKey: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
) {
    companion object {
        /**
         * Convert domain Asset to DTO.
         * Generates publicUrl dynamically from storageKey using the provided baseUrl.
         *
         * @param asset Domain asset model
         * @param baseUrl Base CDN URL for generating publicUrl (e.g., https://cdn.example.com)
         */
        fun fromDomain(asset: Asset, baseUrl: String): AssetDTO {
            // Validate inputs
            require(baseUrl.isNotBlank()) { "baseUrl must not be blank when generating publicUrl" }
            require(asset.storageKey.isNotBlank()) { "asset.storageKey must not be blank when generating publicUrl" }
            
            // Generate publicUrl dynamically from storageKey
            val publicUrl = Asset.generatePublicUrl(baseUrl, asset.storageKey)
            
            return AssetDTO(
                id = asset.id,
                ownerId = asset.ownerId,
                namespace = asset.namespace,
                resourceType = asset.resourceType,
                resourceId = asset.resourceId,
                storageKey = asset.storageKey,
                storageRegion = asset.storageRegion,
                storageBucket = asset.storageBucket,
                mimeType = asset.mimeType,
                sizeBytes = asset.sizeBytes,
                checksum = asset.checksum,
                originalFilename = asset.originalFilename,
                uploadUrl = asset.uploadUrl,
                uploadExpiresAt = asset.uploadExpiresAt,
                publicUrl = publicUrl,
                status = asset.status,
                idempotencyKey = asset.idempotencyKey,
                createdAt = asset.createdAt,
                updatedAt = asset.updatedAt,
                deletedAt = asset.deletedAt,
            )
        }
    }
}
