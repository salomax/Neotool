package io.github.salomax.neotool.assistant.provider.openai

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("llm.openai")
data class OpenAIProviderConfig(
    val baseUrl: String = "https://api.openai.com",
    val apiKeyRef: String = "OPENAI_API_KEY",
    val chatCompletionsPath: String = "/v1/chat/completions",
    val requestTimeoutMs: Long = 60000,
    val userAgent: String = "neotool-assistant/1.0",
)
