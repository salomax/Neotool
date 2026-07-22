package io.github.salomax.neotool.security.service.oauth2

import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.io.InputStreamReader
import java.util.Properties

@Singleton
class OAuth2ScopeDescriptionService(
    private val scopePermissionMapper: OAuth2ScopePermissionMapper,
) {
    private val locales = listOf("en", "pt", "pt-BR")
    private val descriptionsByLocale: Map<String, Properties> =
        locales.associateWith { locale -> loadDescriptions(locale) }

    @PostConstruct
    fun validateDescriptions() {
        val missing =
            locales.flatMap { locale ->
                scopePermissionMapper.allScopes().mapNotNull { scope ->
                    val description = descriptionsByLocale.getValue(locale).getProperty(resourceKey(scope))
                    if (description.isNullOrBlank() || description == FALLBACK_DESCRIPTION) "$locale:$scope" else null
                }
            }
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Missing OAuth scope descriptions: $missing")
        }
    }

    fun describe(
        scopes: String,
        locale: String?,
    ): List<OAuth2ScopeDescription> {
        val descriptions = descriptionsByLocale[resolveLocale(locale)] ?: descriptionsByLocale.getValue("en")
        return parseScopes(scopes).map { scope ->
            OAuth2ScopeDescription(
                name = scope,
                description = descriptions.getProperty(resourceKey(scope), FALLBACK_DESCRIPTION),
            )
        }
    }

    private fun loadDescriptions(locale: String): Properties {
        val path = "templates/oauth2-consent/scopes.$locale.properties"
        val properties = Properties()
        javaClass.classLoader.getResourceAsStream(path)?.use { stream ->
            properties.load(InputStreamReader(stream, Charsets.UTF_8))
        } ?: throw IllegalStateException("Missing OAuth scope description resource: $path")
        return properties
    }

    private fun resolveLocale(locale: String?): String {
        val normalized =
            locale
                ?.split(",")
                ?.firstOrNull()
                ?.split(";")
                ?.firstOrNull()
                ?.trim()
                ?.replace("_", "-")
                ?: return "en"
        return when {
            normalized.equals("pt-BR", ignoreCase = true) -> "pt-BR"
            normalized.startsWith("pt", ignoreCase = true) -> "pt"
            normalized.startsWith("en", ignoreCase = true) -> "en"
            else -> "en"
        }
    }

    private fun parseScopes(scope: String): List<String> =
        scope
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun resourceKey(scope: String): String = scope.replace(":", ".")

    companion object {
        const val FALLBACK_DESCRIPTION = "Access this permission."
    }
}
