package io.github.salomax.neotool.comms.email.validation

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidEmailSendRequestValidatorTest {
    private val validator = ValidEmailSendRequestValidator()
    private val context = mockk<jakarta.validation.ConstraintValidatorContext>(relaxed = true)

    @Test
    fun `accepts valid RAW content`() {
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.RAW,
                        subject = "Hello",
                        body = "World",
                    ),
            )

        val isValid = validator.isValid(request, context)

        assertThat(isValid).isTrue()
    }

    @Test
    fun `rejects RAW content without subject`() {
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.RAW,
                        subject = null,
                        body = "World",
                    ),
            )

        val isValid = validator.isValid(request, context)

        assertThat(isValid).isFalse()
    }

    @Test
    fun `rejects TEMPLATE content without templateKey`() {
        val request =
            EmailSendRequest(
                to = "user@example.com",
                content =
                    EmailContent(
                        kind = EmailContentKind.TEMPLATE,
                        templateKey = null,
                        locale = "en-US",
                    ),
            )

        val isValid = validator.isValid(request, context)

        assertThat(isValid).isFalse()
    }
}
