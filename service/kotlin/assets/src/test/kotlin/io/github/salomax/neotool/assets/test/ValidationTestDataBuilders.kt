package io.github.salomax.neotool.assets.test

import io.github.salomax.neotool.assets.config.NamespaceConfig
import io.github.salomax.neotool.assets.domain.AssetResourceType

/**
 * Test data builders for ValidationService tests.
 *
 * Provides factory methods for creating test data with sensible defaults.
 */
object ValidationTestDataBuilders {
    /**
     * Create a namespace configuration for testing.
     *
     * @param name The namespace name
     * @param maxSizeBytes Maximum file size in bytes
     * @param allowedMimeTypes Set of allowed MIME types
     * @param resourceTypes Set of allowed resource types
     * @return A NamespaceConfig instance with the specified values
     */
    fun namespaceConfig(
        name: String = "user-profiles",
        maxSizeBytes: Long = 10_000_000,
        allowedMimeTypes: Set<String> = setOf("image/jpeg", "image/png"),
        resourceTypes: Set<AssetResourceType> = setOf(AssetResourceType.PROFILE_IMAGE),
    ): NamespaceConfig {
        return NamespaceConfig(
            name = name,
            description = "Test namespace configuration",
            maxSizeBytes = maxSizeBytes,
            allowedMimeTypes = allowedMimeTypes,
            resourceTypes = resourceTypes,
        )
    }

    /**
     * Create a default namespace configuration.
     */
    fun defaultNamespaceConfig(): NamespaceConfig {
        return namespaceConfig(
            name = "default",
            maxSizeBytes = 5_000_000,
            resourceTypes = emptySet(),
        )
    }
}
