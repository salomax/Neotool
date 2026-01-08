package io.github.salomax.neotool.assets.repository

import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.entity.AssetEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Repository for Asset persistence.
 *
 * Provides data access methods for assets following Micronaut Data patterns.
 * Uses nullable return types (Kotlin style) for custom methods.
 * Note: findById from JpaRepository returns Optional - use the extension function findAssetById() for nullable return type.
 */
@Repository
interface AssetRepository : JpaRepository<AssetEntity, UUID> {

    /**
     * Find asset by storage key.
     * Storage key is unique across all assets.
     */
    fun findByStorageKey(storageKey: String): AssetEntity?

    /**
     * Find asset by owner ID and idempotency key.
     * Used for idempotent upload creation (24h window).
     */
    fun findByOwnerIdAndIdempotencyKey(
        ownerId: String,
        idempotencyKey: String,
    ): AssetEntity?

    /**
     * Find all assets for a given owner.
     */
    fun findByOwnerId(ownerId: String): List<AssetEntity>

    /**
     * Find all assets for a given owner with specific status.
     */
    fun findByOwnerIdAndStatus(
        ownerId: String,
        status: AssetStatus,
    ): List<AssetEntity>

    /**
     * Find all assets in a namespace.
     */
    fun findByNamespace(namespace: String): List<AssetEntity>

    /**
     * Find all assets in a namespace with specific status.
     */
    fun findByNamespaceAndStatus(
        namespace: String,
        status: AssetStatus,
    ): List<AssetEntity>

    /**
     * Count assets by owner and status.
     * Used for rate limiting and quota checks.
     */
    fun countByOwnerIdAndStatus(
        ownerId: String,
        status: AssetStatus,
    ): Long

    /**
     * Count assets by namespace and status.
     */
    fun countByNamespaceAndStatus(
        namespace: String,
        status: AssetStatus,
    ): Long

    /**
     * Find all PENDING assets with expired upload URLs.
     * Used by background job to mark failed uploads.
     */
    @Query(
        """
        SELECT a FROM AssetEntity a
        WHERE a.status = 'PENDING'
        AND a.uploadExpiresAt < :now
        """,
    )
    fun findExpiredPendingAssets(now: Instant): List<AssetEntity>

    /**
     * Find all PENDING or FAILED assets older than specified hours.
     * Used by cleanup job to purge stale incomplete uploads.
     */
    @Query(
        """
        SELECT a FROM AssetEntity a
        WHERE a.status IN ('PENDING', 'FAILED')
        AND a.createdAt < :threshold
        """,
    )
    fun findStalePendingOrFailedAssets(threshold: Instant): List<AssetEntity>

    /**
     * Find all DELETED assets older than specified days.
     * Used by purge job to hard delete legacy soft-deleted assets.
     * Note: Assets are now hard-deleted immediately, so this is primarily for legacy data cleanup.
     */
    @Query(
        """
        SELECT a FROM AssetEntity a
        WHERE a.status = 'DELETED'
        AND a.deletedAt IS NOT NULL
        AND a.deletedAt < :threshold
        """,
    )
    fun findOldDeletedAssets(threshold: Instant): List<AssetEntity>

    /**
     * Calculate total bytes uploaded by owner in a time window.
     * Used for rate limiting based on bandwidth.
     */
    @Query(
        """
        SELECT COALESCE(SUM(a.sizeBytes), 0) FROM AssetEntity a
        WHERE a.ownerId = :ownerId
        AND a.status = 'READY'
        AND a.createdAt >= :since
        """,
    )
    fun sumBytesByOwnerSince(
        ownerId: String,
        since: Instant,
    ): Long

    /**
     * Calculate total bytes in namespace.
     * Used for namespace quota checks.
     */
    @Query(
        """
        SELECT COALESCE(SUM(a.sizeBytes), 0) FROM AssetEntity a
        WHERE a.namespace = :namespace
        AND a.status = 'READY'
        """,
    )
    fun sumBytesByNamespace(namespace: String): Long

    /**
     * Delete assets by IDs.
     * Used for hard deletion of legacy soft-deleted assets.
     * Note: Assets are now hard-deleted immediately, so this is primarily for legacy data cleanup.
     */
    fun deleteByIdIn(ids: List<UUID>): Long

    /**
     * Count assets created after a given timestamp for a specific owner.
     * Used for rate limiting by upload count.
     */
    fun countByOwnerIdAndCreatedAtAfter(
        ownerId: String,
        since: Instant,
    ): Long

    /**
     * Sum file sizes by owner ID.
     * Used for storage quota checks.
     */
    @Query(
        """
        SELECT COALESCE(SUM(a.sizeBytes), 0) FROM AssetEntity a
        WHERE a.ownerId = :ownerId
        AND a.status = 'READY'
        """,
    )
    fun sumFileSizeByOwnerId(ownerId: String): Long?
}

/**
 * Extension function to get nullable AssetEntity from Optional.
 * Convenience method for Kotlin code that prefers nullable types over Optional.
 */
fun AssetRepository.findAssetById(id: UUID): AssetEntity? {
    return findById(id).orElse(null)
}
