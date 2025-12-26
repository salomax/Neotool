package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.config.AssetConfigProperties
import io.github.salomax.neotool.assets.domain.AssetResourceType
import jakarta.inject.Singleton

/**
 * Service for validating asset uploads.
 *
 * Validates MIME types and file sizes based on namespace configuration.
 */
@Singleton
class ValidationService(
    private val assetConfig: AssetConfigProperties,
) {
    /**
     * Validate MIME type for a namespace.
     *
     * @param namespace The namespace to validate against
     * @param mimeType The MIME type to validate (case-insensitive)
     * @param resourceType The resource type being validated
     * @throws BadRequestException if MIME type is not allowed for the namespace
     */
    fun validateMimeType(
        namespace: String,
        mimeType: String,
        resourceType: AssetResourceType,
    ) {
        val namespaceConfig = assetConfig.getNamespaceConfig(namespace)
        val allowedMimeTypes = namespaceConfig.allowedMimeTypes

        val normalizedMimeType = mimeType.lowercase()
        val normalizedAllowedTypes = allowedMimeTypes.map { it.lowercase() }

        if (!normalizedAllowedTypes.contains(normalizedMimeType)) {
            throw IllegalArgumentException(
                "Invalid MIME type '$mimeType' for namespace '$namespace'. " +
                    "Allowed types: ${allowedMimeTypes.joinToString(", ")}",
            )
        }
    }

    /**
     * Validate file size for a namespace.
     *
     * @param namespace The namespace to validate against
     * @param sizeBytes The file size in bytes
     * @param resourceType The resource type being validated
     * @throws BadRequestException if file size is invalid or exceeds limit
     */
    fun validateFileSize(
        namespace: String,
        sizeBytes: Long,
        resourceType: AssetResourceType,
    ) {
        if (sizeBytes <= 0) {
            throw IllegalArgumentException("File size must be greater than 0")
        }

        val namespaceConfig = assetConfig.getNamespaceConfig(namespace)
        val maxSizeBytes = namespaceConfig.maxSizeBytes

        if (sizeBytes > maxSizeBytes) {
            throw IllegalArgumentException(
                "File size exceeds limit for namespace '$namespace'. " +
                    "Maximum size: $maxSizeBytes bytes (${maxSizeBytes / 1_000_000} MB)",
            )
        }
    }

    /**
     * Validate both MIME type and file size for a namespace.
     *
     * @param namespace The namespace to validate against
     * @param mimeType The MIME type to validate
     * @param sizeBytes The file size in bytes
     * @param resourceType The resource type being validated
     * @throws BadRequestException if validation fails
     */
    fun validate(
        namespace: String,
        mimeType: String,
        sizeBytes: Long,
        resourceType: AssetResourceType,
    ) {
        validateMimeType(namespace, mimeType, resourceType)
        validateFileSize(namespace, sizeBytes, resourceType)
    }
}
