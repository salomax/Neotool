package io.github.salomax.neotool.comms.email.validation

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind

/**
 * Centralized validation logic for email content.
 * Used by both the Kafka processor (throws exceptions) and
 * the Jakarta Bean Validator (collects violations).
 */
object EmailContentValidator {
    const val TEMPLATE_NOT_SUPPORTED = "Template content is not supported yet"
    const val SUBJECT_REQUIRED = "subject must not be blank"
    const val BODY_REQUIRED = "body must not be blank"
    const val TEMPLATE_KEY_REQUIRED = "templateKey must not be blank"
    const val LOCALE_REQUIRED = "locale must not be blank"

    /**
     * Validates email content and returns a list of validation errors.
     * Empty list means validation passed.
     */
    fun validate(content: EmailContent): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        when (content.kind) {
            EmailContentKind.RAW -> {
                if (content.subject.isNullOrBlank()) {
                    errors.add(ValidationError("content.subject", SUBJECT_REQUIRED))
                }
                if (content.body.isNullOrBlank()) {
                    errors.add(ValidationError("content.body", BODY_REQUIRED))
                }
            }
            EmailContentKind.TEMPLATE -> {
                if (content.templateKey.isNullOrBlank()) {
                    errors.add(ValidationError("content.templateKey", TEMPLATE_KEY_REQUIRED))
                }
                if (content.locale.isNullOrBlank()) {
                    errors.add(ValidationError("content.locale", LOCALE_REQUIRED))
                }
            }
        }

        return errors
    }

    /**
     * Checks if the content kind is currently supported.
     */
    fun isKindSupported(kind: EmailContentKind): Boolean {
        return kind == EmailContentKind.RAW
    }

    data class ValidationError(
        val field: String,
        val message: String,
    )
}
