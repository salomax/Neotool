package io.github.salomax.neotool.comms.email.dto

import io.github.salomax.neotool.comms.email.validation.ValidEmailSendRequest
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Introspected
@Serdeable
@ValidEmailSendRequest
data class EmailSendRequest(
    @field:NotBlank(message = "to must not be blank")
    @field:Email(message = "to must be a valid email address")
    val to: String,
    @field:NotNull(message = "content must not be null")
    val content: EmailContent,
)
