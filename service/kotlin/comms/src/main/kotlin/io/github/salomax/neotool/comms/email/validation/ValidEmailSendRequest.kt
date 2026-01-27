package io.github.salomax.neotool.comms.email.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidEmailSendRequestValidator::class])
annotation class ValidEmailSendRequest(
    val message: String = "Invalid email content",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
