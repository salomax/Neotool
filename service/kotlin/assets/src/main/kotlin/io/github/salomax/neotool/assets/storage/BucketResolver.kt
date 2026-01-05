package io.github.salomax.neotool.assets.storage

import io.github.salomax.neotool.assets.domain.AssetVisibility
import jakarta.inject.Singleton

/**
 * Resolves bucket name based on asset visibility.
 *
 * Uses Strategy pattern to encapsulate bucket selection logic.
 * This makes it easier to test and extend (e.g., future multi-tenant bucket selection).
 */
interface BucketResolver {
    /**
     * Resolve bucket name for a given visibility level.
     *
     * @param visibility Asset visibility (PUBLIC or PRIVATE)
     * @return Bucket name for the visibility level
     */
    fun resolveBucket(visibility: AssetVisibility): String

    /**
     * Get public base URL for generating public CDN URLs.
     *
     * @return Public base URL (e.g., https://cdn.example.com/assets)
     */
    fun getPublicBaseUrl(): String
}

/**
 * Visibility-based bucket resolver implementation.
 *
 * Maps PUBLIC assets to public bucket, PRIVATE assets to private bucket.
 */
@Singleton
class VisibilityBasedBucketResolver(
    private val storageProperties: StorageProperties,
) : BucketResolver {
    override fun resolveBucket(visibility: AssetVisibility): String {
        return when (visibility) {
            AssetVisibility.PUBLIC -> storageProperties.publicBucket
            AssetVisibility.PRIVATE -> storageProperties.privateBucket
        }
    }

    override fun getPublicBaseUrl(): String {
        return storageProperties.getPublicBaseUrl()
    }
}
