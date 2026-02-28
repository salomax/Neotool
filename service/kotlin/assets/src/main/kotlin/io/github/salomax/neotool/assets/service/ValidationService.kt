package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.config.AssetConfigProperties
import io.github.salomax.neotool.assets.error.AssetErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton

/**
 * Service for validating asset uploads.
 *
 * Validates MIME types and file sizes based on namespace configuration.
 */
@TraceExecution
@Singleton
class ValidationService(
    private val assetConfig: AssetConfigProperties,
) {
    /**
     * Validate MIME type for a namespace.
     *
     * @param namespace The namespace to validate against
     * @param mimeType The MIME type to validate (case-insensitive)
     * @throws IllegalArgumentException if MIME type is not allowed for the namespace
     */
    fun validateMimeType(
        namespace: String,
        mimeType: String,
    ) {
        val namespaceConfig = assetConfig.getNamespaceConfig(namespace)
        val allowedMimeTypes = namespaceConfig.allowedMimeTypes

        val normalizedMimeType = mimeType.lowercase()
        val normalizedAllowedTypes = allowedMimeTypes.map { it.lowercase() }

        if (!normalizedAllowedTypes.contains(normalizedMimeType)) {
            throw ValidationException(
                errorCode = AssetErrorCode.ASSET_FORMAT_UNSUPPORTED,
                field = "mimeType",
                parameters =
                    mapOf(
                        "namespace" to namespace,
                        "mimeType" to mimeType,
                        "allowedMimeTypes" to allowedMimeTypes.joinToString(", "),
                    ),
            )
        }
    }

    /**
     * Validate file size for a namespace.
     *
     * @param namespace The namespace to validate against
     * @param sizeBytes The file size in bytes
     * @throws IllegalArgumentException if file size is invalid or exceeds limit
     */
    fun validateFileSize(
        namespace: String,
        sizeBytes: Long,
    ) {
        if (sizeBytes <= 0) {
            throw ValidationException(
                errorCode = AssetErrorCode.ASSET_SIZE_INVALID,
                field = "sizeBytes",
                parameters = mapOf("sizeBytes" to sizeBytes),
            )
        }

        val namespaceConfig = assetConfig.getNamespaceConfig(namespace)
        val maxSizeBytes = namespaceConfig.maxSizeBytes

        if (sizeBytes > maxSizeBytes) {
            throw ValidationException(
                errorCode = AssetErrorCode.ASSET_SIZE_EXCEEDED,
                field = "sizeBytes",
                parameters =
                    mapOf(
                        "namespace" to namespace,
                        "maxSizeBytes" to maxSizeBytes,
                        "sizeBytes" to sizeBytes,
                    ),
            )
        }
    }

    /**
     * Validate both MIME type and file size for a namespace.
     *
     * @param namespace The namespace to validate against
     * @param mimeType The MIME type to validate
     * @param sizeBytes The file size in bytes
     * @throws IllegalArgumentException if validation fails
     */
    fun validate(
        namespace: String,
        mimeType: String,
        sizeBytes: Long,
    ) {
        validateMimeType(namespace, mimeType)
        validateFileSize(namespace, sizeBytes)
    }
}
