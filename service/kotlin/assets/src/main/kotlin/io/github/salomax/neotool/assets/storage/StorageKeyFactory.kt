package io.github.salomax.neotool.assets.storage

import io.github.salomax.neotool.assets.config.NamespaceConfig
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * Factory for generating storage keys from namespace-configured templates.
 *
 * Supports placeholders:
 * - {namespace}: Namespace name
 * - {ownerId}: Owner ID (user or system ID)
 * - {assetId}: Asset UUID
 *
 * Example templates:
 * - "user-profiles/{ownerId}/{assetId}" -> "user-profiles/user-123/01234567-89ab-cdef-0123-456789abcdef"
 * - "public/institution-logo/{assetId}" -> "public/institution-logo/01234567-89ab-cdef-0123-456789abcdef"
 */
@Singleton
class StorageKeyFactory {
    private val logger = KotlinLogging.logger {}

    /**
     * Build storage key from template and variables.
     *
     * @param namespaceConfig Namespace configuration containing keyTemplate
     * @param ownerId Owner ID (user or system ID)
     * @param assetId Asset UUID
     * @return Storage key with placeholders replaced
     * @throws IllegalArgumentException if template is invalid or key format is invalid
     */
    fun buildKey(
        namespaceConfig: NamespaceConfig,
        ownerId: String,
        assetId: UUID,
    ): String {
        val template = namespaceConfig.keyTemplate
        val namespace = namespaceConfig.name

        // Validate template contains required placeholders (already validated in config loading, but double-check)
        if (!template.contains("{namespace}")) {
            throw IllegalArgumentException(
                "keyTemplate for namespace '$namespace' must contain {namespace} placeholder: $template",
            )
        }
        if (!template.contains("{assetId}")) {
            throw IllegalArgumentException(
                "keyTemplate for namespace '$namespace' must contain {assetId} placeholder: $template",
            )
        }

        // Replace placeholders
        var key =
            template
                .replace("{namespace}", namespace)
                .replace("{ownerId}", ownerId)
                .replace("{assetId}", assetId.toString())

        // Validate resulting key format
        validateKeyFormat(namespace, key)

        logger.debug { "Generated storage key for namespace '$namespace': $key" }
        return key
    }

    /**
     * Validate storage key format.
     *
     * Ensures:
     * - No empty segments (double slashes)
     * - No leading or trailing slashes
     * - Valid characters (alphanumeric, hyphens, underscores, slashes)
     * - Not empty
     */
    private fun validateKeyFormat(
        namespace: String,
        key: String,
    ) {
        if (key.isBlank()) {
            throw IllegalArgumentException(
                "Generated storage key for namespace '$namespace' is empty",
            )
        }

        if (key.contains("//")) {
            throw IllegalArgumentException(
                "Generated storage key for namespace '$namespace' contains empty segments (//): $key",
            )
        }

        if (key.startsWith("/") || key.endsWith("/")) {
            throw IllegalArgumentException(
                "Generated storage key for namespace '$namespace' has leading or trailing slash: $key",
            )
        }

        // Validate characters (alphanumeric, hyphens, underscores, slashes, dots)
        val validPattern = Regex("^[a-zA-Z0-9._/-]+$")
        if (!validPattern.matches(key)) {
            throw IllegalArgumentException(
                "Generated storage key for namespace '$namespace' contains invalid characters: $key",
            )
        }
    }
}
