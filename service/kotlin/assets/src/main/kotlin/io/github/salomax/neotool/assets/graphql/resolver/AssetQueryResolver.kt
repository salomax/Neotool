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

}
