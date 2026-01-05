package io.github.salomax.neotool.assets.storage

/**
 * Storage client interface for object storage operations.
 *
 * Provides abstraction over different storage providers (S3, R2, GCS, Azure Blob, etc.).
 * Implementations handle provider-specific details while maintaining consistent interface.
 *
 * Benefits:
 * - Easy to swap storage providers (S3 -> R2 -> GCS)
 * - Mockable for testing
 * - Support multiple providers simultaneously (multi-cloud)
 * - Supports separate buckets for public and private assets
 */
interface StorageClient {
    /**
     * Generate a pre-signed PUT URL for direct client upload.
     *
     * @param bucket Bucket name where the object will be stored
     * @param storageKey Unique storage key for the object
     * @param mimeType Content type for the upload
     * @param ttlSeconds Time-to-live for the pre-signed URL
     * @return Pre-signed URL string
     */
    fun generatePresignedUploadUrl(
        bucket: String,
        storageKey: String,
        mimeType: String,
        ttlSeconds: Long,
    ): String

    /**
     * Generate a pre-signed GET URL for temporary download access.
     *
     * Used for private assets that require signed URLs.
     *
     * @param bucket Bucket name where the object is stored
     * @param storageKey Unique storage key for the object
     * @param ttlSeconds Time-to-live for the pre-signed URL
     * @return Pre-signed URL string
     */
    fun generatePresignedDownloadUrl(
        bucket: String,
        storageKey: String,
        ttlSeconds: Long,
    ): String

    /**
     * Check if an object exists in storage.
     *
     * @param bucket Bucket name where the object should be stored
     * @param storageKey Unique storage key for the object
     * @return true if object exists, false otherwise
     */
    fun objectExists(
        bucket: String,
        storageKey: String,
    ): Boolean

    /**
     * Get object metadata (size, content-type, etc.).
     *
     * @param bucket Bucket name where the object is stored
     * @param storageKey Unique storage key for the object
     * @return Object metadata or null if not found
     */
    fun getObjectMetadata(
        bucket: String,
        storageKey: String,
    ): ObjectMetadata?

    /**
     * Delete an object from storage.
     *
     * @param bucket Bucket name where the object is stored
     * @param storageKey Unique storage key for the object
     * @return true if deleted successfully, false if object didn't exist
     */
    fun deleteObject(
        bucket: String,
        storageKey: String,
    ): Boolean

    /**
     * Generate public URL for an asset.
     *
     * For public assets accessible via CDN without signed URLs.
     * Should only be called for public bucket assets.
     *
     * @param bucket Bucket name (should be public bucket)
     * @param storageKey Unique storage key for the object
     * @return Public CDN URL
     */
    fun generatePublicUrl(
        bucket: String,
        storageKey: String,
    ): String

    /**
     * Object metadata returned from storage.
     */
    data class ObjectMetadata(
        val sizeBytes: Long,
        val contentType: String?,
        val etag: String?,
    )
}
