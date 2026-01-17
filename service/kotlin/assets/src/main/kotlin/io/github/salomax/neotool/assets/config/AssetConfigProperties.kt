package io.github.salomax.neotool.assets.config

import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.micronaut.context.annotation.Property
import io.micronaut.core.io.ResourceLoader
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * Asset configuration properties loaded from asset-config.yml.
 *
 * Provides namespace-based validation rules.
 */
@Singleton
class AssetConfigProperties(
    private val resourceLoader: ResourceLoader,
    @Property(name = "assets.config.file", defaultValue = "asset-config.yml")
    private val configFileName: String,
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
    fun getNamespaceConfig(namespace: String): NamespaceConfig =
        config.namespaces[namespace]
            ?: config.namespaces["default"]
            ?: throw IllegalStateException("Default namespace configuration not found")

    /**
     * Load configuration from asset-config.yml.
     */
    private fun loadConfig(): AssetConfig {
        logger.info { "Loading asset configuration from $configFileName" }

        val inputStream: InputStream =
            resourceLoader
                .getResourceAsStream("classpath:$configFileName")
                .orElseThrow { IllegalStateException("$configFileName not found in classpath") }

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

        logger.info { "Asset configuration loaded successfully: ${namespaces.size} namespaces configured" }

        return AssetConfig(namespaces)
    }

    /**
     * Parse namespace configuration from YAML map.
     */
    private fun parseNamespaceConfig(
        name: String,
        configMap: Map<String, Any>,
    ): NamespaceConfig {
        val description = configMap["description"] as? String ?: ""

        val visibilityString =
            (configMap["visibility"] as? String)
                ?: throw IllegalStateException("visibility not found for namespace: $name")
        val visibility =
            try {
                AssetVisibility.valueOf(visibilityString)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(
                    "Invalid visibility '$visibilityString' for namespace: $name. " +
                        "Valid values: ${AssetVisibility.entries.joinToString()}",
                    e,
                )
            }

        val keyTemplate =
            (configMap["keyTemplate"] as? String)
                ?: throw IllegalStateException("keyTemplate not found for namespace: $name")

        // Validate keyTemplate contains required placeholders
        validateKeyTemplate(name, keyTemplate)

        val maxSizeBytes =
            (configMap["maxSizeBytes"] as? Number)?.toLong()
                ?: throw IllegalStateException("maxSizeBytes not found for namespace: $name")

        val allowedMimeTypes =
            (configMap["allowedMimeTypes"] as? List<String>)
                ?: throw IllegalStateException("allowedMimeTypes not found for namespace: $name")

        val uploadTtlSeconds =
            (configMap["uploadTtlSeconds"] as? Number)?.toLong()

        return NamespaceConfig(
            name = name,
            description = description,
            visibility = visibility,
            keyTemplate = keyTemplate,
            maxSizeBytes = maxSizeBytes,
            allowedMimeTypes = allowedMimeTypes.toSet(),
            uploadTtlSeconds = uploadTtlSeconds,
        )
    }

    /**
     * Validate key template contains required placeholders.
     *
     * Required placeholders: {namespace}, {assetId}
     * Optional placeholders: {ownerId}
     */
    private fun validateKeyTemplate(
        namespace: String,
        keyTemplate: String,
    ) {
        if (!keyTemplate.contains("{namespace}")) {
            throw IllegalStateException(
                "keyTemplate for namespace '$namespace' must contain {namespace} placeholder: $keyTemplate",
            )
        }
        if (!keyTemplate.contains("{assetId}")) {
            throw IllegalStateException(
                "keyTemplate for namespace '$namespace' must contain {assetId} placeholder: $keyTemplate",
            )
        }

        // TODO valida using regex?
        // Validate no empty segments (double slashes or leading/trailing slashes after replacement)
        val testKey =
            keyTemplate
                .replace("{namespace}", "test")
                .replace("{ownerId}", "test")
                .replace("{assetId}", "test")

        if (testKey.contains("//") || testKey.startsWith("/") || testKey.endsWith("/")) {
            throw IllegalStateException(
                "keyTemplate for namespace '$namespace' produces invalid key format: $keyTemplate",
            )
        }
    }
}

/**
 * Complete asset configuration.
 */
data class AssetConfig(
    val namespaces: Map<String, NamespaceConfig>,
)

/**
 * Configuration for a namespace.
 */
data class NamespaceConfig(
    val name: String,
    val description: String,
    val visibility: AssetVisibility,
    val keyTemplate: String,
    val maxSizeBytes: Long,
    val allowedMimeTypes: Set<String>,
    // Optional override, falls back to global default
    val uploadTtlSeconds: Long? = null,
)
