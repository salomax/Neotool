package io.github.salomax.neotool.assets.domain

import java.time.Instant
import java.util.UUID

/**
 * Pure domain model for an Asset.
 *
 * Represents an uploaded file (image, document, etc.) with its metadata.
 * This is a pure domain object following DDD principles - no JPA annotations.
 *
 * @property id Unique identifier (UUID v7 - time-sortable)
 * @property ownerId User ID or system ID that owns this asset
 * @property namespace Logical grouping (e.g., user-profiles, group-assets)
 * @property visibility Visibility level (PUBLIC or PRIVATE) - determines access control and URL generation
 * @property storageKey Unique key in S3/R2 storage
 * @property storageRegion Storage region (e.g., us-east-1)
 * @property storageBucket Storage bucket name
 * @property mimeType MIME type (e.g., image/jpeg, application/pdf)
 * @property sizeBytes File size in bytes (null until confirmed)
 * @property checksum SHA-256 checksum for integrity verification
 * @property originalFilename Original filename from client upload
 * @property uploadUrl Pre-signed upload URL (temporary, null after upload)
 * @property uploadExpiresAt When the upload URL expires
 * @property publicUrl Public CDN URL for accessing the asset (deprecated - use generatePublicUrl() instead)
 * @property status Upload status (PENDING, READY, FAILED, DELETED)
 * @property idempotencyKey Client-provided key to prevent duplicate uploads
 * @property createdAt Timestamp when asset record was created
 * @property updatedAt Timestamp when asset record was last updated
 * @property deletedAt Timestamp when asset was deleted (null if active, deprecated - assets are now hard deleted)
 */
data class Asset(
    val id: UUID? = null,
    val ownerId: String,
    val namespace: String,
    val visibility: AssetVisibility,
    val storageKey: String,
    val storageRegion: String,
    val storageBucket: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val checksum: String?,
    val originalFilename: String?,
    val uploadUrl: String?,
    val uploadExpiresAt: Instant?,
    val publicUrl: String? = null, // Deprecated - generated dynamically from storageKey
    val status: AssetStatus,
    val idempotencyKey: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
) {
    /**
     * Check if the asset is ready for use.
     */
    fun isReady(): Boolean = status == AssetStatus.READY

    /**
     * Check if the asset is pending upload.
     */
    fun isPending(): Boolean = status == AssetStatus.PENDING

    /**
     * Check if the asset upload has failed.
     */
    fun isFailed(): Boolean = status == AssetStatus.FAILED

    /**
     * Check if the asset is deleted.
     * Note: Assets are now hard-deleted, so this status should rarely be encountered.
     */
    fun isDeleted(): Boolean = status == AssetStatus.DELETED

    /**
     * Check if the upload URL has expired.
     */
    fun isUploadExpired(): Boolean {
        val expiresAt = uploadExpiresAt ?: return true
        return Instant.now().isAfter(expiresAt)
    }

    /**
     * Check if the asset can be uploaded to (PENDING and not expired).
     */
    fun canUpload(): Boolean = isPending() && !isUploadExpired()

    /** TODO remove it
     * Get the public URL if the asset is ready, null otherwise.
     * @deprecated Use generatePublicUrl() with baseUrl parameter instead
     */
    @Deprecated("Use generatePublicUrl(baseUrl, storageKey) instead")
    fun getPublicUrlIfReady(): String? = if (isReady()) publicUrl else null

    /**
     * Generate public URL from storage key and base URL.
     * This method generates the URL dynamically based on the current environment configuration.
     * Only valid for PUBLIC assets - PRIVATE assets should use presigned download URLs.
     *
     * @param baseUrl Base CDN URL (e.g., https://cdn.example.com)
     * @return Public CDN URL
     */
    fun generatePublicUrl(baseUrl: String): String = generatePublicUrl(baseUrl, storageKey)

    companion object {
        /**
         * Generate a public URL for an asset.
         * Only valid for PUBLIC assets - PRIVATE assets should use presigned download URLs.
         *
         * @param baseUrl Base CDN URL (e.g., https://cdn.example.com)
         * @param storageKey Storage key path
         * @return Public CDN URL
         */
        fun generatePublicUrl(
            baseUrl: String,
            storageKey: String,
        ): String {
            val cleanBaseUrl = baseUrl.trimEnd('/')
            return "$cleanBaseUrl/$storageKey"
        }
    }
}
