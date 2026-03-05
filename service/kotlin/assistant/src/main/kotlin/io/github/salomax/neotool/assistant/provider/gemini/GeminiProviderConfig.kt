package io.github.salomax.neotool.assistant.provider.gemini

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("llm.gemini")
data class GeminiProviderConfig(
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val apiKey: String = "",
    val generateContentPath: String = "/v1beta/models/__MODEL__:generateContent",
    val requestTimeoutMs: Long = 60000,
    val userAgent: String = "neotool-assistant/1.0",
)
