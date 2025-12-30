package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.config.AssetConfigProperties
import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.assets.storage.StorageClient
import io.github.salomax.neotool.assets.storage.StorageKeyFactory
import io.github.salomax.neotool.assets.storage.StorageProperties
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Asset service for managing asset upload lifecycle.
 *
 * Handles:
 * - Asset upload initiation (generate pre-signed URLs)
 * - Asset confirmation (mark as uploaded)
 * - Asset retrieval (generate download URLs)
 * - Asset deletion
 */
@Singleton
@Transactional
open class AssetService(
    private val assetRepository: AssetRepository,
    private val storageClient: StorageClient,
    private val storageProperties: StorageProperties,
    private val validationService: ValidationService,
    private val assetConfig: AssetConfigProperties,
    private val storageKeyFactory: StorageKeyFactory,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Initiate asset upload.
     *
     * Creates PENDING asset record and generates pre-signed upload URL.
     *
     * @param namespace Configuration namespace (e.g., "user-profiles", "group-assets")
     * @param ownerId User ID of the asset owner
     * @param filename Original filename
     * @param mimeType MIME type of the file
     * @param sizeBytes File size in bytes
     * @param idempotencyKey Optional key to prevent duplicate uploads
     * @return Asset domain object with uploadUrl
     */
    open fun initiateUpload(
        namespace: String,
        ownerId: String,
        filename: String,
        mimeType: String,
        sizeBytes: Long,
        idempotencyKey: String? = null,
    ): Asset {
        logger.info {
            "Initiating upload: namespace=$namespace, ownerId=$ownerId, " +
                "filename=$filename, mimeType=$mimeType, sizeBytes=$sizeBytes"
        }

        // Get namespace configuration
        val namespaceConfig = assetConfig.getNamespaceConfig(namespace)

        // Check for existing upload with same idempotency key (within 24 hours)
        if (idempotencyKey != null) {
            val existing = findByIdempotencyKey(ownerId, idempotencyKey)
            if (existing != null) {
                logger.info { "Returning existing asset for idempotency key: $idempotencyKey, assetId=${existing.id}" }
                return existing
            }
        }

        // Validate MIME type and file size
        validationService.validate(namespace, mimeType, sizeBytes)

        // Derive TTL: namespace override or global default
        val uploadTtlSeconds = namespaceConfig.uploadTtlSeconds ?: storageProperties.uploadTtlSeconds

        // Calculate upload expiration
        val uploadExpiresAt = Instant.now().plus(uploadTtlSeconds, ChronoUnit.SECONDS)

        // Create pending asset entity with temporary storage key
        // Database generates UUID v7, then we'll update storage key with actual ID
        // Note: publicUrl is no longer stored - generated dynamically from storageKey
        val entity =
            AssetEntity(
                id = null,
                ownerId = ownerId,
                namespace = namespace,
                visibility = namespaceConfig.visibility,
                // Temporary, will be updated after save
                storageKey = "temp/${UUID.randomUUID()}",
                storageRegion = storageProperties.region,
                storageBucket = storageProperties.bucket,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksum = null,
                originalFilename = filename,
                // Upload URL will be set after generation
                uploadUrl = null,
                uploadExpiresAt = uploadExpiresAt,
                // No longer stored - generated dynamically
                publicUrl = null,
                status = AssetStatus.PENDING,
                idempotencyKey = idempotencyKey,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                deletedAt = null,
            )

        // Save entity to get database-generated UUID v7
        val saved = assetRepository.save(entity)
        val assetId =
            saved.id
                ?: throw IllegalStateException("Database did not generate UUID v7 for asset")

        // Generate storage key using template from namespace config with actual asset ID
        val storageKey = storageKeyFactory.buildKey(namespaceConfig, ownerId, assetId)

        // Update entity with correct storage key
        saved.storageKey = storageKey
        val updated = assetRepository.update(saved)

        logger.info { "Created PENDING asset: id=$assetId, storageKey=$storageKey, visibility=${updated.visibility}" }

        // Generate pre-signed upload URL using namespace TTL
        val uploadUrl =
            storageClient.generatePresignedUploadUrl(
                storageKey,
                mimeType,
                uploadTtlSeconds,
            )

        // Update entity with uploadUrl
        updated.uploadUrl = uploadUrl
        val final = assetRepository.update(updated)

        return final.toDomain()
    }

    /**
     * Confirm asset upload.
     *
     * Marks asset as READY after client completes upload to storage.
     * Verifies that file exists in storage before confirming.
     *
     * @param assetId Asset ID
     * @param ownerId User ID of the asset owner (for authorization)
     * @param checksum Optional SHA-256 checksum for verification
     * @return Updated asset domain object
     */
    open fun confirmUpload(
        assetId: UUID,
        ownerId: String,
        checksum: String? = null,
    ): Asset {
        logger.info { "Confirming upload: assetId=$assetId, ownerId=$ownerId" }

        val entity =
            assetRepository
                .findById(assetId)
                .orElseThrow {
                    IllegalArgumentException("Asset not found: $assetId")
                }

        // Authorization check
        if (entity.ownerId != ownerId) {
            logger.warn { "Unauthorized upload confirmation attempt: assetId=$assetId, ownerId=$ownerId" }
            throw IllegalArgumentException("Asset not found or access denied: $assetId")
        }

        // Status check
        if (entity.status != AssetStatus.PENDING) {
            throw IllegalArgumentException("Asset is not in PENDING status: ${entity.status}")
        }

        // Check if upload has expired
        val now = Instant.now()
        if (entity.uploadExpiresAt != null && now.isAfter(entity.uploadExpiresAt)) {
            logger.warn { "Upload confirmation failed: URL expired at ${entity.uploadExpiresAt}" }
            entity.status = AssetStatus.FAILED
            assetRepository.update(entity)
            throw IllegalArgumentException("Upload URL has expired")
        }

        // Verify object exists in storage
        if (!storageClient.objectExists(entity.storageKey)) {
            logger.error { "Asset confirmation failed: object not found in storage: ${entity.storageKey}" }
            entity.status = AssetStatus.FAILED
            entity.uploadUrl = null // Clear stale upload URL
            entity.updatedAt = now
            assetRepository.update(entity)
            throw IllegalArgumentException("Upload not completed: file not found in storage")
        }

        // Get metadata from storage to verify
        val metadata = storageClient.getObjectMetadata(entity.storageKey)
        if (metadata == null) {
            logger.error { "Asset confirmation failed: could not retrieve metadata: ${entity.storageKey}" }
            entity.status = AssetStatus.FAILED
            entity.uploadUrl = null // Clear stale upload URL
            entity.updatedAt = now
            assetRepository.update(entity)
            throw IllegalArgumentException("Upload verification failed: could not retrieve file metadata")
        }

        // Verify checksum if provided
        if (checksum != null && metadata.etag != null) {
            // S3 ETag is MD5 for simple uploads, but may differ for multipart
            // For now, just store the provided checksum
            entity.checksum = checksum
            logger.debug { "Checksum provided: $checksum" }
        }

        // Update entity status to READY
        entity.status = AssetStatus.READY
        entity.sizeBytes = metadata.sizeBytes
        entity.updatedAt = now
        entity.uploadUrl = null // Clear upload URL for security

        val updated = assetRepository.update(entity)
        logger.info { "Asset upload confirmed: id=${updated.id}, storageKey=${updated.storageKey}" }

        return updated.toDomain()
    }

    /**
     * Get asset by ID.
     *
     * Visibility-based authorization:
     * - PUBLIC assets: Accessible without owner check (but still return null if missing)
     * - PRIVATE assets: Require ownership check
     *
     * @param assetId Asset ID
     * @param requesterId User ID requesting the asset (for authorization)
     * @return Asset domain object or null if not found/unauthorized
     */
    open fun getAsset(
        assetId: UUID,
        requesterId: String,
    ): Asset? {
        val entity =
            assetRepository
                .findById(assetId)
                .orElse(null)
                ?: return null

        // Visibility-based authorization
        when (entity.visibility) {
            AssetVisibility.PUBLIC -> {
                // PUBLIC assets: accessible without owner check
                logger.debug { "Accessing PUBLIC asset: assetId=$assetId, requesterId=$requesterId" }
            }
            AssetVisibility.PRIVATE -> {
                // PRIVATE assets: require ownership
                if (entity.ownerId != requesterId) {
                    logger.warn { "Unauthorized asset access attempt: assetId=$assetId, requesterId=$requesterId" }
                    return null // Don't reveal existence
                }
            }
        }

        return entity.toDomain()
    }

    /**
     * Generate presigned download URL for a PRIVATE asset.
     *
     * This method:
     * - Verifies the asset exists and requester has access (authorization check)
     * - Only generates URLs for PRIVATE assets (PUBLIC assets use publicUrl)
     * - Caps client-requested TTL to configured maximum (downloadTtlSeconds)
     *
     * @param assetId Asset ID
     * @param requesterId User ID requesting the download (for authorization)
     * @param clientTtlSeconds Optional client-requested TTL (will be capped to configured maximum)
     * @return Presigned download URL or null if asset not found/unauthorized/PUBLIC
     */
    open fun generateDownloadUrl(
        assetId: UUID,
        requesterId: String,
        clientTtlSeconds: Long? = null,
    ): String? {
        logger.debug {
            "Generating download URL: assetId=$assetId, requesterId=$requesterId, clientTtlSeconds=$clientTtlSeconds"
        }

        // Get asset (this performs authorization check)
        val asset = getAsset(assetId, requesterId) ?: return null

        // Only generate download URLs for PRIVATE assets
        // PUBLIC assets should use publicUrl instead
        if (asset.visibility != AssetVisibility.PRIVATE) {
            logger.debug { "Skipping download URL generation for PUBLIC asset: assetId=$assetId" }
            return null
        }

        // Cap client-supplied TTL to configured maximum
        // This prevents clients from requesting arbitrarily long-lived URLs
        val ttlSeconds =
            if (clientTtlSeconds != null) {
                minOf(clientTtlSeconds, storageProperties.downloadTtlSeconds)
            } else {
                storageProperties.downloadTtlSeconds
            }

        logger.debug { "Generating presigned download URL with TTL: $ttlSeconds seconds" }
        return storageClient.generatePresignedDownloadUrl(asset.storageKey, ttlSeconds)
    }

    /**
     * Delete asset.
     *
     * Hard-deletes from database and deletes from storage.
     *
     * @param assetId Asset ID
     * @param ownerId User ID of the asset owner (for authorization)
     */
    open fun deleteAsset(
        assetId: UUID,
        ownerId: String,
    ): Boolean {
        logger.info { "Deleting asset: assetId=$assetId, ownerId=$ownerId" }

        val entity =
            assetRepository
                .findById(assetId)
                .orElse(null)
                ?: return false

        // Authorization check
        if (entity.ownerId != ownerId) {
            logger.warn { "Unauthorized delete attempt: assetId=$assetId, ownerId=$ownerId" }
            return false
        }

        // Delete from storage (best effort - don't fail if already deleted)
        try {
            storageClient.deleteObject(entity.storageKey)
            logger.debug { "Deleted from storage: ${entity.storageKey}" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to delete from storage (continuing): ${entity.storageKey}" }
        }

        // Hard delete from database
        assetRepository.delete(entity)

        logger.info { "Asset hard-deleted: id=$assetId, storageKey=${entity.storageKey}" }
        return true
    }

    /**
     * Find asset by idempotency key (within 24 hour window).
     *
     * @param ownerId Owner ID
     * @param idempotencyKey Idempotency key
     * @return Asset if found within 24 hours, null otherwise
     */
    private fun findByIdempotencyKey(
        ownerId: String,
        idempotencyKey: String,
    ): Asset? {
        val entity =
            assetRepository
                .findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey)
                .orElse(null)
                ?: return null

        // Check if asset is within 24 hour window
        val now = Instant.now()
        val createdAt = entity.createdAt
        val hoursSinceCreation = ChronoUnit.HOURS.between(createdAt, now)

        if (hoursSinceCreation > 24) {
            logger.debug { "Idempotency key expired (> 24h): $idempotencyKey" }
            return null
        }

        return entity.toDomain()
    }
}
