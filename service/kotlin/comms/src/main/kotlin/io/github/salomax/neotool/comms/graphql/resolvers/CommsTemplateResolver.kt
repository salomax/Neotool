package io.github.salomax.neotool.comms.graphql.resolvers

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.service.TemplateService
import jakarta.inject.Singleton
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Singleton
class CommsTemplateResolver(
    private val templateService: TemplateService,
) {
    fun listTemplates(channel: String): List<Map<String, Any?>> {
        val parsedChannel = parseChannel(channel)
        val templates = templateService.listTemplates(parsedChannel)

        return templates.map { template ->
            mapOf(
                "key" to template.key,
                "name" to template.metadata.name,
                "description" to template.metadata.description,
                "channel" to template.channel.name,
                "supportedLocales" to
                    template.locales.keys.map { it.toString() },
                "variables" to
                    template.variables.map { variable ->
                        mapOf(
                            "name" to variable.name,
                            "type" to variable.type.name,
                            "required" to variable.required,
                            "default" to variable.default?.toString(),
                            "description" to variable.description,
                        )
                    },
            )
        }
    }

    fun getTemplate(
        key: String,
        channel: String,
    ): Map<String, Any?>? {
        val parsedChannel = parseChannel(channel)
        val template = templateService.getTemplate(key, parsedChannel) ?: return null

        return mapOf(
            "key" to template.key,
            "name" to template.metadata.name,
            "description" to template.metadata.description,
            "channel" to template.channel.name,
            "supportedLocales" to
                template.locales.keys.map { it.toString() },
            "defaultLocale" to template.defaultLocale.toString(),
            "variables" to
                template.variables.map { variable ->
                    mapOf(
                        "name" to variable.name,
                        "type" to variable.type.name,
                        "required" to variable.required,
                        "default" to variable.default?.toString(),
                        "description" to variable.description,
                    )
                },
            "owner" to template.metadata.owner,
        )
    }

    private fun parseChannel(channelStr: String): Channel {
        return try {
            Channel.valueOf(channelStr.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid channel: $channelStr" }
            val validValues = Channel.values().joinToString { it.name }
            throw IllegalArgumentException(
                "Invalid channel: $channelStr. Valid values: $validValues",
            )
        }
    }
}
