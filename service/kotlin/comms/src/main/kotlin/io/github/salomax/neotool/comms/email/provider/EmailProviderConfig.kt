package io.github.salomax.neotool.comms.email.provider

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("comms.email")
data class EmailProviderConfig(
    val provider: String = "mock",
    val from: String = "noreply@neotool.com",
)
