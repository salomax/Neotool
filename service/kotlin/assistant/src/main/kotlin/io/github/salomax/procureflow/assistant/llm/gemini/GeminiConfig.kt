package io.github.salomax.neotool.assistant.llm.gemini

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("llm.gemini")
data class GeminiConfig(
    val apiKey: String,
    val model: String = "gemini-1.5-pro",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
)
