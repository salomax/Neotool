package io.github.salomax.neotool.assets.domain

/**
 * Visibility level for assets.
 *
 * Determines access control and URL generation strategy:
 * - PUBLIC: Accessible without owner check, uses stable CDN URLs
 * - PRIVATE: Requires ownership check, uses presigned download URLs
 */
enum class AssetVisibility {
    /**
     * Public asset - accessible without owner check.
     * Uses stable CDN URLs generated from storage key.
     */
    PUBLIC,

    /**
     * Private asset - requires ownership check.
     * Uses presigned download URLs with TTL.
     */
    PRIVATE,
}
