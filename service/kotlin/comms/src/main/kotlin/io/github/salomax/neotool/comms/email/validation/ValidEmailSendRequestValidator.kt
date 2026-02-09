package io.github.salomax.neotool.comms.email.validation

import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class ValidEmailSendRequestValidator : ConstraintValidator<ValidEmailSendRequest, EmailSendRequest> {
    override fun isValid(
        value: EmailSendRequest?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) {
            return true
        }

        val errors = EmailContentValidator.validate(value.content)
        if (errors.isEmpty()) {
            return true
        }

        context.disableDefaultConstraintViolation()
        errors.forEach { error ->
            context
                .buildConstraintViolationWithTemplate(error.message)
                .addPropertyNode(error.field)
                .addConstraintViolation()
        }

        return false
    }
}
