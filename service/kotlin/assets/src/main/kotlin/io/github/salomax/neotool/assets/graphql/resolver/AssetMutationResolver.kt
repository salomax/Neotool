package io.github.salomax.neotool.assets.graphql.resolver

import io.github.salomax.neotool.assets.graphql.dto.AssetDTO
import io.github.salomax.neotool.assets.graphql.dto.ConfirmAssetUploadInput
import io.github.salomax.neotool.assets.graphql.dto.CreateAssetUploadInput
import io.github.salomax.neotool.assets.service.AssetService
import io.github.salomax.neotool.assets.storage.BucketResolver
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL Mutation resolver for Asset operations.
 *
 * Handles write operations for assets (create, update, delete).
 */
@Singleton
class AssetMutationResolver(
    private val assetService: AssetService,
    private val bucketResolver: BucketResolver,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Create asset upload and get pre-signed URL.
     *
     * GraphQL Mutation:
     * ```graphql
     * mutation {
     *   createAssetUpload(input: {
     *     namespace: "user-profiles"
     *     filename: "avatar.jpg"
     *     mimeType: "image/jpeg"
     *     sizeBytes: 1048576
     *     idempotencyKey: "unique-key-123"
     *   }) {
     *     id
     *     uploadUrl
     *     uploadExpiresAt
     *     status
     *     visibility
     *     storageKey
     *   }
     * }
     * ```
     *
     * Note: The `namespace` parameter determines:
     * - Storage key template (configured per namespace)
     * - Visibility level (PUBLIC or PRIVATE)
     * - Validation rules (allowed MIME types, max file size)
     * - Upload URL TTL
     *
     * @param input Create asset upload input
     * @param userId User ID of the requester (extracted from authentication context)
     * @return Asset DTO with uploadUrl
     */
    fun createAssetUpload(
        input: CreateAssetUploadInput,
        userId: String,
    ): AssetDTO {
        logger.info {
            "Creating asset upload: namespace=${input.namespace}, userId=$userId"
        }

        val asset =
            assetService.initiateUpload(
                namespace = input.namespace,
                ownerId = userId,
                filename = input.filename,
                mimeType = input.mimeType,
                sizeBytes = input.sizeBytes,
                idempotencyKey = input.idempotencyKey,
            )

        logger.info { "Asset upload created: assetId=${asset.id}, uploadUrl present=${asset.uploadUrl != null}" }
        return AssetDTO.fromDomain(asset, bucketResolver.getPublicBaseUrl())
    }

    /**
     * Confirm asset upload after client completes file upload.
     *
     * GraphQL Mutation:
     * ```graphql
     * mutation {
     *   confirmAssetUpload(input: {
     *     assetId: "01234567-89ab-cdef-0123-456789abcdef"
     *     checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
     *   }) {
     *     id
     *     status
     *     publicUrl
     *     downloadUrl(ttlSeconds: 3600)
     *     visibility
     *   }
     * }
     * ```
     *
     * Note:
     * - For PUBLIC assets: `publicUrl` will be populated with a stable CDN URL
     * - For PRIVATE assets: `publicUrl` will be null, use `downloadUrl` instead
     *
     * @param input Confirm asset upload input
     * @param userId User ID of the requester (extracted from authentication context)
     * @return Updated asset DTO
     */
    fun confirmAssetUpload(
        input: ConfirmAssetUploadInput,
        userId: String,
    ): AssetDTO {
        logger.info { "Confirming asset upload: assetId=${input.assetId}, userId=$userId" }

        val asset =
            assetService.confirmUpload(
                assetId = input.assetId,
                ownerId = userId,
                checksum = input.checksum,
            )

        logger.info { "Asset upload confirmed: assetId=${asset.id}, status=${asset.status}" }
        return AssetDTO.fromDomain(asset, bucketResolver.getPublicBaseUrl())
    }

    /**
     * Delete asset.
     *
     * GraphQL Mutation:
     * ```graphql
     * mutation {
     *   deleteAsset(assetId: "01234567-89ab-cdef-0123-456789abcdef")
     * }
     * ```
     *
     * @param assetId Asset ID
     * @param userId User ID of the requester (extracted from authentication context)
     * @return True if deleted successfully
     */
    fun deleteAsset(
        assetId: String,
        userId: String,
    ): Boolean {
        logger.info { "Deleting asset: assetId=$assetId, userId=$userId" }

        val deleted = assetService.deleteAsset(java.util.UUID.fromString(assetId), userId)

        if (deleted) {
            logger.info { "Asset deleted: assetId=$assetId" }
        } else {
            logger.warn { "Asset delete failed: assetId=$assetId (not found or unauthorized)" }
        }

        return deleted
    }
}
