package io.github.salomax.neotool.security.service.oauth2

import jakarta.inject.Singleton

@Singleton
class OAuth2ClientMetadataCodec {
    fun encodeList(values: List<String>): String = values.joinToString("\n")

    fun decodeList(value: String): List<String> =
        value
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
}
