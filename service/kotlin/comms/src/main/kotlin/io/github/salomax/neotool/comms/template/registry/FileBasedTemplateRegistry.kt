package io.github.salomax.neotool.comms.template.registry

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateContent
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.domain.TemplateMetadata
import io.github.salomax.neotool.comms.template.domain.VariableDefinition
import io.github.salomax.neotool.comms.template.domain.VariableType
import io.micronaut.context.annotation.Property
import io.micronaut.core.io.ResourceLoader
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Safe cast for YAML/JSON-untyped structures. YAML returns Map<String, Any> but Kotlin sees erased generics.
 * Centralizes the single unchecked cast with a clear contract: only use for trusted template config.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> Any?.asTypedOrNull(): T? = this as? T

/**
 * File-based template registry implementation.
 *
 * Loads templates from YAML files in `resources/templates/{channel}/{template-key}/template.yml`
 * and caches them in memory for fast lookup.
 */
@Singleton
class FileBasedTemplateRegistry(
    private val resourceLoader: ResourceLoader,
    @param:Property(name = "comms.template.directory", defaultValue = "templates")
    private val templateDirectory: String,
    @param:Property(name = "comms.template.default-locale", defaultValue = "en")
    private val systemDefaultLocale: String,
    @param:Property(name = "comms.template.hot-reload", defaultValue = "false")
    private val hotReloadEnabled: Boolean,
) : TemplateRegistry {
    private val logger = KotlinLogging.logger {}

    // Thread-safe cache: channel -> (key -> TemplateDefinition)
    private val cache = ConcurrentHashMap<Channel, MutableMap<String, TemplateDefinition>>()

    // Lock for reload operations
    private val reloadLock = ReentrantReadWriteLock()

    @PostConstruct
    fun initialize() {
        loadTemplates()
    }

    override fun resolve(
        key: String,
        locale: Locale,
        channel: Channel,
    ): TemplateDefinition? {
        reloadLock.read {
            val channelTemplates = cache[channel] ?: return null
            val template = channelTemplates[key] ?: return null

            // Resolve locale with fallback
            val resolvedLocale =
                LocaleResolver.resolve(locale, template.locales.keys, template.defaultLocale)
                    ?: return null

            // Return template with resolved locale content
            // Note: The template already contains all locales, so we just return it
            // The caller will use the resolved locale to get the right content
            return template
        }
    }

    override fun listByChannel(channel: Channel): List<TemplateDefinition> {
        reloadLock.read {
            return cache[channel]?.values?.toList() ?: emptyList()
        }
    }

    override fun reload() {
        if (!hotReloadEnabled) {
            logger.warn { "Hot reload requested but disabled. Set comms.template.hot-reload=true to enable." }
            return
        }

        reloadLock.write {
            logger.info { "Reloading templates from $templateDirectory" }
            cache.clear()
            loadTemplates()
            logger.info { "Templates reloaded successfully" }
        }
    }

    /**
     * Load all templates from the templates directory.
     */
    private fun loadTemplates() {
        logger.info { "Loading templates from $templateDirectory" }

        val templates = mutableListOf<Pair<TemplateDefinition, File>>()

        // Scan for template directories by channel
        Channel.values().forEach { channel ->
            val channelPath = "$templateDirectory/${channel.name.lowercase()}"
            loadTemplatesForChannel(channel, channelPath, templates)
        }

        if (templates.isEmpty()) {
            logger.warn { "No templates found in $templateDirectory" }
            return
        }

        // Validate all templates
        val validationErrors = TemplateValidator.validateAll(templates)
        if (validationErrors.isNotEmpty()) {
            val errorMessages =
                validationErrors.entries.joinToString("\n") { (key, errors) ->
                    "Template '$key':\n  " + errors.joinToString("\n  ") { it.message }
                }
            throw IllegalStateException(
                "Template validation failed:\n$errorMessages",
            )
        }

        // Build cache
        templates.forEach { (template, _) ->
            cache.getOrPut(template.channel) { mutableMapOf() }[template.key] = template
        }

        val totalTemplates = templates.size
        val templatesByChannel = templates.groupBy { it.first.channel }
        logger.info {
            "Loaded $totalTemplates templates: " +
                templatesByChannel.map { "${it.key}=${it.value.size}" }.joinToString(", ")
        }
    }

    /**
     * Load templates for a specific channel.
     */
    private fun loadTemplatesForChannel(
        channel: Channel,
        channelPath: String,
        templates: MutableList<Pair<TemplateDefinition, File>>,
    ) {
        // Try to discover templates by scanning for template.yml files
        // We use a simple approach: try to find template directories by attempting to load template.yml
        // This works for both file system and JAR resources

        // First, try to scan file system resources (for development)
        val classLoader = javaClass.classLoader
        try {
            val resources = classLoader.getResources(channelPath)
            while (resources.hasMoreElements()) {
                val resource = resources.nextElement()
                if (resource.protocol == "file") {
                    val file = File(resource.toURI())
                    if (file.isDirectory) {
                        file.listFiles()?.forEach { templateDir ->
                            if (templateDir.isDirectory) {
                                val template = loadTemplateFromClasspath(channel, templateDir.name, channelPath)
                                if (template != null) {
                                    templates.add(Pair(template, templateDir))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Could not scan file system resources for channel $channel" }
        }

        // Also try loading known templates from classpath
        // This works for both file system and JAR resources
        discoverTemplatesFromClasspath(channel, channelPath, templates)
    }

    /**
     * Discover templates by trying to load template.yml files.
     * This method attempts to find templates by trying common patterns or scanning.
     */
    private fun discoverTemplatesFromClasspath(
        channel: Channel,
        channelPath: String,
        templates: MutableList<Pair<TemplateDefinition, File>>,
    ) {
        // Use a simple discovery mechanism: try to load template.yml files
        // We'll try common template key patterns first
        val knownKeys = mutableSetOf<String>()

        // Try to discover templates by attempting to load template.yml files
        // This is a simple approach - in production you might use a manifest file
        // For email channel, try known template keys
        val templateKeysToTry =
            when (channel) {
                Channel.EMAIL -> listOf("user-welcome", "order-confirmation", "password-reset", "verify-email")
                Channel.PUSH -> listOf("chat-notification", "order-update")
                Channel.WHATSAPP -> listOf("appointment-reminder", "order-confirmation")
                Channel.IN_APP -> listOf("notification", "alert")
                Channel.CHAT -> listOf("message", "notification")
            }

        val existingKeys = templates.map { it.first.key }.toSet()
        templateKeysToTry.forEach { key ->
            val templatePath = "$channelPath/$key/template.yml"
            val resource = resourceLoader.getResourceAsStream("classpath:$templatePath").orElse(null)
            if (resource != null) {
                val template = loadTemplateFromClasspath(channel, key, channelPath)
                if (
                    template != null &&
                    !knownKeys.contains(template.key) &&
                    !existingKeys.contains(template.key)
                ) {
                    knownKeys.add(template.key)
                    // Create a dummy File for validation (path only)
                    val dummyFile = File("/$channelPath/$key")
                    templates.add(Pair(template, dummyFile))
                }
            }
        }
    }

    /**
     * Load a template from classpath resources.
     */
    private fun loadTemplateFromClasspath(
        channel: Channel,
        templateKey: String,
        channelPath: String,
    ): TemplateDefinition? {
        val templateYmlPath = "$channelPath/$templateKey/template.yml"
        val templateYmlResource =
            resourceLoader.getResourceAsStream("classpath:$templateYmlPath").orElse(null)
                ?: return null

        return try {
            val yaml = Yaml()
            val templateMap = yaml.load<Map<String, Any>>(templateYmlResource)

            // Parse template definition (same logic as loadTemplate but for classpath)
            parseTemplateDefinition(channel, templateKey, templateMap, channelPath, templateKey)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load template from classpath: $templateYmlPath" }
            null
        }
    }

    /**
     * Parse template definition from YAML map.
     */
    private fun parseTemplateDefinition(
        channel: Channel,
        templateKey: String,
        templateMap: Map<String, Any>,
        basePath: String,
        key: String,
    ): TemplateDefinition? {
        try {
            val parsedKey = templateMap["key"] as? String ?: templateKey
            val channelStr = (templateMap["channel"] as? String)?.uppercase() ?: channel.name
            val parsedChannel =
                try {
                    Channel.valueOf(channelStr)
                } catch (e: IllegalArgumentException) {
                    logger.error(e) { "Invalid channel: $channelStr" }
                    return null
                }

            // Parse metadata
            val metadataMap = templateMap["metadata"].asTypedOrNull<Map<String, Any>>() ?: emptyMap()
            val metadata =
                TemplateMetadata(
                    name = metadataMap["name"] as? String ?: parsedKey,
                    description = metadataMap["description"] as? String,
                    owner = metadataMap["owner"] as? String,
                )

            // Parse variables
            val variablesList = templateMap["variables"].asTypedOrNull<List<Map<String, Any>>>() ?: emptyList()
            val variables =
                variablesList.map { varMap ->
                    VariableDefinition(
                        name = varMap["name"] as? String ?: "",
                        type =
                            try {
                                VariableType.valueOf((varMap["type"] as? String ?: "STRING").uppercase())
                            } catch (e: IllegalArgumentException) {
                                VariableType.STRING
                            },
                        required = varMap["required"] as? Boolean ?: true,
                        default = varMap["default"],
                        description = varMap["description"] as? String,
                    )
                }

            // Parse locales
            val localesMap =
                templateMap["locales"].asTypedOrNull<Map<String, Map<String, Any>>>() ?: emptyMap()
            val locales = mutableMapOf<Locale, TemplateContent>()

            localesMap.forEach { (localeStr, localeMap) ->
                val locale = parseLocale(localeStr)
                val subject = localeMap["subject"] as? String
                val bodyPath = localeMap["bodyPath"] as? String

                if (bodyPath == null) {
                    logger.warn { "bodyPath not found for locale $localeStr in template $parsedKey" }
                    return@forEach
                }

                // Load body content from classpath
                val bodyResourcePath = "$basePath/$key/$bodyPath"
                val bodyResource =
                    resourceLoader.getResourceAsStream("classpath:$bodyResourcePath").orElse(null)
                        ?: run {
                            logger.warn { "Body file not found: $bodyResourcePath" }
                            return@forEach
                        }

                val body = bodyResource.bufferedReader().use { it.readText() }

                // Parse channel config
                val channelConfigMap =
                    templateMap["channelConfig"].asTypedOrNull<Map<String, Any>>() ?: emptyMap()
                val channelConfig =
                    channelConfigMap[channel.name.lowercase()]
                        .asTypedOrNull<Map<String, Any>>() ?: emptyMap()

                locales[locale] =
                    TemplateContent(
                        locale = locale,
                        subject = subject,
                        body = body,
                        channelConfig = channelConfig,
                    )
            }

            // Parse default locale
            val defaultLocaleStr = templateMap["defaultLocale"] as? String ?: systemDefaultLocale
            val defaultLocale = parseLocale(defaultLocaleStr)

            return TemplateDefinition(
                key = parsedKey,
                channel = parsedChannel,
                metadata = metadata,
                variables = variables,
                locales = locales,
                defaultLocale = defaultLocale,
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse template definition for $templateKey" }
            return null
        }
    }

    /**
     * Parse locale string to Locale object.
     */
    private fun parseLocale(localeStr: String): Locale {
        // Normalize to BCP 47 format (use - instead of _)
        val normalized = localeStr.replace("_", "-")
        return try {
            Locale.forLanguageTag(normalized)
        } catch (e: Exception) {
            // Fallback to simple language tag if parsing fails
            Locale.Builder().setLanguageTag(normalized).build()
        }
    }
}
