package io.github.salomax.neotool.assets.graphql.resolver

import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.graphql.dto.AssetDTO
import io.github.salomax.neotool.assets.service.AssetService
import io.github.salomax.neotool.assets.storage.StorageProperties
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * GraphQL Query resolver for Asset operations.
 *
 * Handles read operations for assets.
 */
@Singleton
class AssetQueryResolver(
    private val assetService: AssetService,
    private val storageProperties: StorageProperties,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get asset by ID.
     *
     * GraphQL Query:
     * ```graphql
     * query {
     *   asset(id: "01234567-89ab-cdef-0123-456789abcdef") {
     *     id
     *     status
     *     publicUrl
     *   }
     * }
     * ```
     *
     * @param id Asset ID
     * @param requesterId User ID of the requester (extracted from authentication context)
     * @return Asset DTO or null if not found/unauthorized
     */
    fun asset(
        id: String,
        requesterId: String,
    ): AssetDTO? {
        val assetId = UUID.fromString(id)
        val asset = assetService.getAsset(assetId, requesterId) ?: return null
        return AssetDTO.fromDomain(asset, storageProperties.getPublicBaseUrl())
    }

    /**
     * Get assets by resource.
     *
     * GraphQL Query:
     * ```graphql
     * query {
     *   assetsByResource(resourceType: PROFILE_IMAGE, resourceId: "user-123") {
     *     id
     *     publicUrl
     *     status
     *   }
     * }
     * ```
     *
     * @param resourceType Type of resource
     * @param resourceId ID of the resource
     * @return List of assets
     */
    fun assetsByResource(
        resourceType: AssetResourceType,
        resourceId: String,
    ): List<AssetDTO> {
        val assets = assetService.findByResource(resourceType, resourceId)
        val baseUrl = storageProperties.getPublicBaseUrl()
        return assets.map { AssetDTO.fromDomain(it, baseUrl) }
    }

    /**
     * Get assets by owner.
     *
     * GraphQL Query:
     * ```graphql
     * query {
     *   assetsByOwner(ownerId: "user-123", status: READY, limit: 20) {
     *     id
     *     resourceType
     *     publicUrl
     *   }
     * }
     * ```
     *
     * @param ownerId Owner ID
     * @param status Optional status filter
     * @param limit Maximum results (default 50)
     * @param offset Pagination offset (default 0)
     * @return List of assets
     */
    fun assetsByOwner(
        ownerId: String,
        status: AssetStatus? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<AssetDTO> {
        val assets = assetService.findByOwner(ownerId, status, limit, offset)
        val baseUrl = storageProperties.getPublicBaseUrl()
        return assets.map { AssetDTO.fromDomain(it, baseUrl) }
    }

    /**
     * Get assets by namespace.
     *
     * GraphQL Query:
     * ```graphql
     * query {
     *   assetsByNamespace(namespace: "user-profiles", status: PENDING) {
     *     id
     *     ownerId
     *     uploadExpiresAt
     *   }
     * }
     * ```
     *
     * @param namespace Namespace
     * @param status Optional status filter
     * @param limit Maximum results (default 50)
     * @param offset Pagination offset (default 0)
     * @return List of assets
     */
    fun assetsByNamespace(
        namespace: String,
        status: AssetStatus? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<AssetDTO> {
        val assets = assetService.findByNamespace(namespace, status, limit, offset)
        val baseUrl = storageProperties.getPublicBaseUrl()
        return assets.map { AssetDTO.fromDomain(it, baseUrl) }
    }

}
