package io.github.salomax.neotool.comms.email.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class EmailContent(
    val kind: EmailContentKind,
    val format: EmailBodyFormat = EmailBodyFormat.TEXT,
    val subject: String? = null,
    val body: String? = null,
    val templateKey: String? = null,
    val locale: String? = null,
    val variables: Map<String, Any?> = emptyMap(),
)
