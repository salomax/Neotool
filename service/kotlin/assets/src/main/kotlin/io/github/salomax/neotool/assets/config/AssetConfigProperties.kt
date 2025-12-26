package io.github.salomax.neotool.assets.config

import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.micronaut.core.io.ResourceLoader
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * Asset configuration properties loaded from asset-config.yml.
 *
 * Provides namespace-based validation rules and global rate limits.
 */
@Singleton
class AssetConfigProperties(
    private val resourceLoader: ResourceLoader,
) {
    private val logger = KotlinLogging.logger {}

    private val config: AssetConfig by lazy {
        loadConfig()
    }

    /**
     * Get namespace configuration by namespace name.
     *
     * Falls back to "default" namespace if not found.
     */
    fun getNamespaceConfig(namespace: String): NamespaceConfig {
        return config.namespaces[namespace]
            ?: config.namespaces["default"]
            ?: throw IllegalStateException("Default namespace configuration not found")
    }

    /**
     * Get rate limit configuration.
     */
    fun getRateLimitConfig(): RateLimitConfig {
        return config.rateLimit
    }

    /**
     * Load configuration from asset-config.yml.
     */
    private fun loadConfig(): AssetConfig {
        logger.info { "Loading asset configuration from asset-config.yml" }

        val inputStream: InputStream =
            resourceLoader.getResourceAsStream("classpath:asset-config.yml")
                .orElseThrow { IllegalStateException("asset-config.yml not found in classpath") }

        val yaml = Yaml()
        val configMap = yaml.load<Map<String, Any>>(inputStream)

        // Parse namespaces
        val namespacesMap =
            configMap["namespaces"] as? Map<String, Map<String, Any>>
                ?: throw IllegalStateException("namespaces configuration not found")

        val namespaces =
            namespacesMap.mapValues { (name, namespaceMap) ->
                parseNamespaceConfig(name, namespaceMap)
            }

        // Parse rate limits
        val rateLimitMap =
            configMap["rateLimit"] as? Map<String, Any>
                ?: throw IllegalStateException("rateLimit configuration not found")

        val rateLimit =
            RateLimitConfig(
                uploadsPerHour = (rateLimitMap["uploadsPerHour"] as? Int) ?: 100,
                uploadsPerDay = (rateLimitMap["uploadsPerDay"] as? Int) ?: 1000,
                storageQuotaBytes = (rateLimitMap["storageQuotaBytes"] as? Number)?.toLong() ?: 1073741824L,
            )

        logger.info { "Asset configuration loaded successfully: ${namespaces.size} namespaces configured" }

        return AssetConfig(namespaces, rateLimit)
    }

    /**
     * Parse namespace configuration from YAML map.
     */
    private fun parseNamespaceConfig(
        name: String,
        configMap: Map<String, Any>,
    ): NamespaceConfig {
        val description = configMap["description"] as? String ?: ""
        val maxSizeBytes =
            (configMap["maxSizeBytes"] as? Number)?.toLong()
                ?: throw IllegalStateException("maxSizeBytes not found for namespace: $name")

        val allowedMimeTypes =
            (configMap["allowedMimeTypes"] as? List<String>)
                ?: throw IllegalStateException("allowedMimeTypes not found for namespace: $name")

        val resourceTypeStrings = (configMap["resourceTypes"] as? List<String>) ?: emptyList()
        val resourceTypes =
            resourceTypeStrings.mapNotNull { typeString ->
                try {
                    AssetResourceType.valueOf(typeString)
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid resource type in namespace $name: $typeString" }
                    null
                }
            }

        return NamespaceConfig(
            name = name,
            description = description,
            maxSizeBytes = maxSizeBytes,
            allowedMimeTypes = allowedMimeTypes.toSet(),
            resourceTypes = resourceTypes.toSet(),
        )
    }
}

/**
 * Complete asset configuration.
 */
data class AssetConfig(
    val namespaces: Map<String, NamespaceConfig>,
    val rateLimit: RateLimitConfig,
)

/**
 * Configuration for a namespace.
 */
data class NamespaceConfig(
    val name: String,
    val description: String,
    val maxSizeBytes: Long,
    val allowedMimeTypes: Set<String>,
    val resourceTypes: Set<AssetResourceType>,
)

/**
 * Global rate limit configuration.
 */
data class RateLimitConfig(
    val uploadsPerHour: Int,
    val uploadsPerDay: Int,
    val storageQuotaBytes: Long,
)
