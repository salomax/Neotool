package io.github.salomax.neotool.framework.graphql

import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator

/**
 * Wraps javax/jakarta Bean Validation to be injected and reused.
 */
@Singleton
class InputValidator(private val validator: Validator) {
    fun <T> validate(bean: T) {
        val violations = validator.validate(bean)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }
}
