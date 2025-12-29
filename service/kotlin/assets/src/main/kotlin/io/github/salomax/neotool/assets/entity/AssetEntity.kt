package io.github.salomax.neotool.assets.entity

import io.github.salomax.neotool.assets.domain.Asset
import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for Asset table.
 *
 * Maps the pure domain model [Asset] to the database table.
 * Follows the pattern of separating domain logic from persistence concerns.
 */
@Entity
@Table(
    name = "assets",
    schema = "assets",
    indexes = [
        Index(name = "idx_assets_owner_id", columnList = "owner_id"),
        Index(name = "idx_assets_namespace", columnList = "namespace"),
        Index(name = "idx_assets_resource", columnList = "resource_type,resource_id"),
        Index(name = "idx_assets_status", columnList = "status"),
        Index(name = "idx_assets_created_at", columnList = "created_at"),
        Index(name = "idx_assets_owner_status", columnList = "owner_id,status"),
        Index(name = "idx_assets_namespace_status", columnList = "namespace,status"),
        Index(name = "idx_assets_visibility", columnList = "visibility"),
        Index(name = "idx_assets_owner_namespace", columnList = "owner_id,namespace"),
    ],
)
open class AssetEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "owner_id", nullable = false, length = 255)
    open var ownerId: String,
    @Column(name = "namespace", nullable = false, length = 100)
    open var namespace: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    open var visibility: AssetVisibility = AssetVisibility.PRIVATE,
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    open var resourceType: AssetResourceType,
    @Column(name = "resource_id", nullable = false, length = 255)
    open var resourceId: String,
    @Column(name = "storage_key", nullable = false, length = 500, unique = true)
    open var storageKey: String,
    @Column(name = "storage_region", nullable = false, length = 50)
    open var storageRegion: String,
    @Column(name = "storage_bucket", nullable = false, length = 100)
    open var storageBucket: String,
    @Column(name = "mime_type", nullable = false, length = 100)
    open var mimeType: String,
    @Column(name = "size_bytes", nullable = true)
    open var sizeBytes: Long? = null,
    @Column(name = "checksum", nullable = true, length = 255)
    open var checksum: String? = null,
    @Column(name = "original_filename", nullable = true, length = 500)
    open var originalFilename: String? = null,
    @Column(name = "upload_url", nullable = true, columnDefinition = "TEXT")
    open var uploadUrl: String? = null,
    @Column(name = "upload_expires_at", nullable = true)
    open var uploadExpiresAt: Instant? = null,
    @Column(name = "public_url", nullable = true, columnDefinition = "TEXT")
    open var publicUrl: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    open var status: AssetStatus,
    @Column(name = "idempotency_key", nullable = true, length = 255)
    open var idempotencyKey: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Column(name = "deleted_at", nullable = true)
    open var deletedAt: Instant? = null,
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    /**
     * Convert JPA entity to pure domain model.
     */
    fun toDomain(): Asset =
        Asset(
            id = id,
            ownerId = ownerId,
            namespace = namespace,
            visibility = visibility,
            resourceType = resourceType,
            resourceId = resourceId,
            storageKey = storageKey,
            storageRegion = storageRegion,
            storageBucket = storageBucket,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            checksum = checksum,
            originalFilename = originalFilename,
            uploadUrl = uploadUrl,
            uploadExpiresAt = uploadExpiresAt,
            publicUrl = publicUrl,
            status = status,
            idempotencyKey = idempotencyKey,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )

    companion object {
        /**
         * Convert domain model to JPA entity.
         */
        fun fromDomain(asset: Asset): AssetEntity =
            AssetEntity(
                id = asset.id,
                ownerId = asset.ownerId,
                namespace = asset.namespace,
                visibility = asset.visibility,
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
                publicUrl = asset.publicUrl,
                status = asset.status,
                idempotencyKey = asset.idempotencyKey,
                createdAt = asset.createdAt,
                updatedAt = asset.updatedAt,
                deletedAt = asset.deletedAt,
            )
    }
}
