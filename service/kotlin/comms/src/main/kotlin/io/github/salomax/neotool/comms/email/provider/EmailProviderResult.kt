package io.github.salomax.neotool.comms.email.provider

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class EmailProviderResult(
    val providerId: String,
    val messageId: String? = null,
)
