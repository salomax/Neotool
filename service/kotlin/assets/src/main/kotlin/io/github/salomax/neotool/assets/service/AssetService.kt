package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.assets.storage.StorageClient
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
    private val rateLimitService: RateLimitService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Initiate asset upload.
     *
     * Creates PENDING asset record and generates pre-signed upload URL.
     *
     * @param namespace Configuration namespace (e.g., "user-profiles", "group-assets")
     * @param resourceType Type of asset (PROFILE_IMAGE, ATTACHMENT, etc.)
     * @param resourceId ID of the resource this asset belongs to
     * @param ownerId User ID of the asset owner
     * @param filename Original filename
     * @param mimeType MIME type of the file
     * @param sizeBytes File size in bytes
     * @param idempotencyKey Optional key to prevent duplicate uploads
     * @return Asset domain object with uploadUrl
     */
    open fun initiateUpload(
        namespace: String,
        resourceType: AssetResourceType,
        resourceId: String,
        ownerId: String,
        filename: String,
        mimeType: String,
        sizeBytes: Long,
        idempotencyKey: String? = null,
    ): Asset {
        logger.info {
            "Initiating upload: namespace=$namespace, resourceType=$resourceType, resourceId=$resourceId, " +
                "ownerId=$ownerId, filename=$filename, mimeType=$mimeType, sizeBytes=$sizeBytes"
        }

        // Check for existing upload with same idempotency key (within 24 hours)
        if (idempotencyKey != null) {
            val existing = findByIdempotencyKey(ownerId, idempotencyKey)
            if (existing != null) {
                logger.info { "Returning existing asset for idempotency key: $idempotencyKey, assetId=${existing.id}" }
                return existing
            }
        }

        // Validate MIME type and file size
        validationService.validate(namespace, mimeType, sizeBytes, resourceType)


        // Generate storage key
        val storageKey = Asset.generateStorageKey(namespace, resourceType, resourceId, UUID.randomUUID())

        // Calculate upload expiration (15 minutes from now)
        val uploadExpiresAt = Instant.now().plus(storageProperties.uploadTtlSeconds, ChronoUnit.SECONDS)

        // Create pending asset entity
        // Database generates UUID v7
        // Note: publicUrl is no longer stored - generated dynamically from storageKey
        val entity =
            AssetEntity(
                id = null,
                ownerId = ownerId,
                namespace = namespace,
                resourceType = resourceType,
                resourceId = resourceId,
                storageKey = storageKey,
                storageRegion = storageProperties.region,
                storageBucket = storageProperties.bucket,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksum = null,
                originalFilename = filename,
                // Upload URL will be set after generation
                uploadUrl = null,
                uploadExpiresAt = uploadExpiresAt,
                publicUrl = null, // No longer stored - generated dynamically
                status = AssetStatus.PENDING,
                idempotencyKey = idempotencyKey,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                deletedAt = null,
            )

        val saved = assetRepository.save(entity)
        logger.info { "Created PENDING asset: id=${saved.id}, storageKey=$storageKey" }

        // Generate pre-signed upload URL
        val uploadUrl =
            storageClient.generatePresignedUploadUrl(
                storageKey,
                mimeType,
                storageProperties.uploadTtlSeconds,
            )

        // Update entity with uploadUrl
        saved.uploadUrl = uploadUrl
        val updated = assetRepository.update(saved)

        return updated.toDomain()
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
            throw IllegalArgumentException("Upload not completed: file not found in storage")
        }

        // Get metadata from storage to verify
        val metadata = storageClient.getObjectMetadata(entity.storageKey)
        if (metadata == null) {
            logger.error { "Asset confirmation failed: could not retrieve metadata: ${entity.storageKey}" }
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
     * @param assetId Asset ID
     * @param requesterId User ID requesting the asset (for authorization)
     * @return Asset domain object or null if not found
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

        // Authorization: Only owner can access their assets
        // In a real system, you might have admin roles or other access rules
        if (entity.ownerId != requesterId) {
            logger.warn { "Unauthorized asset access attempt: assetId=$assetId, requesterId=$requesterId" }
            return null // Don't reveal existence
        }

        return entity.toDomain()
    }

    /**
     * Delete asset.
     *
     * Soft-deletes from database and deletes from storage.
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

        // Soft delete in database
        entity.status = AssetStatus.DELETED
        entity.deletedAt = Instant.now()
        entity.updatedAt = Instant.now()
        assetRepository.update(entity)

        logger.info { "Asset soft-deleted: id=$assetId, storageKey=${entity.storageKey}" }
        return true
    }

    /**
     * Find assets by resource.
     *
     * @param resourceType Type of resource
     * @param resourceId ID of the resource
     * @return List of assets
     */
    open fun findByResource(
        resourceType: AssetResourceType,
        resourceId: String,
    ): List<Asset> {
        val entities = assetRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
        return entities.map { it.toDomain() }
    }

    /**
     * Find assets by owner.
     *
     * @param ownerId Owner ID
     * @param status Optional status filter
     * @param limit Maximum number of results
     * @param offset Offset for pagination
     * @return List of assets
     */
    open fun findByOwner(
        ownerId: String,
        status: AssetStatus? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<Asset> {
        val entities =
            if (status != null) {
                assetRepository.findByOwnerIdAndStatus(ownerId, status)
            } else {
                assetRepository.findByOwnerId(ownerId)
            }

        return entities
            .drop(offset)
            .take(limit)
            .map { it.toDomain() }
    }

    /**
     * Find assets by namespace.
     *
     * @param namespace Namespace
     * @param status Optional status filter
     * @param limit Maximum number of results
     * @param offset Offset for pagination
     * @return List of assets
     */
    open fun findByNamespace(
        namespace: String,
        status: AssetStatus? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<Asset> {
        val entities =
            if (status != null) {
                assetRepository.findByNamespaceAndStatus(namespace, status)
            } else {
                assetRepository.findByNamespace(namespace)
            }

        return entities
            .drop(offset)
            .take(limit)
            .map { it.toDomain() }
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
