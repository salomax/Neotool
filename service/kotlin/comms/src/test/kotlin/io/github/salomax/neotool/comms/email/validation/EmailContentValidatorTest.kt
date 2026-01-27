package io.github.salomax.neotool.comms.email.validation

import io.github.salomax.neotool.comms.email.dto.EmailContent
import io.github.salomax.neotool.comms.email.dto.EmailContentKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmailContentValidatorTest {
    @Test
    fun `validates RAW content with subject and body`() {
        val content = EmailContent(
            kind = EmailContentKind.RAW,
            subject = "Hello",
            body = "World",
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `rejects RAW content with blank subject`() {
        val content = EmailContent(
            kind = EmailContentKind.RAW,
            subject = "  ",
            body = "World",
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).hasSize(1)
        assertThat(errors.first().field).isEqualTo("content.subject")
        assertThat(errors.first().message).isEqualTo(EmailContentValidator.SUBJECT_REQUIRED)
    }

    @Test
    fun `rejects RAW content with null subject`() {
        val content = EmailContent(
            kind = EmailContentKind.RAW,
            subject = null,
            body = "World",
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).hasSize(1)
        assertThat(errors.first().message).isEqualTo(EmailContentValidator.SUBJECT_REQUIRED)
    }

    @Test
    fun `rejects RAW content with blank body`() {
        val content = EmailContent(
            kind = EmailContentKind.RAW,
            subject = "Hello",
            body = "",
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).hasSize(1)
        assertThat(errors.first().field).isEqualTo("content.body")
        assertThat(errors.first().message).isEqualTo(EmailContentValidator.BODY_REQUIRED)
    }

    @Test
    fun `rejects RAW content with both subject and body blank`() {
        val content = EmailContent(
            kind = EmailContentKind.RAW,
            subject = null,
            body = null,
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).hasSize(2)
    }

    @Test
    fun `validates TEMPLATE content with templateKey and locale`() {
        val content = EmailContent(
            kind = EmailContentKind.TEMPLATE,
            templateKey = "welcome",
            locale = "en-US",
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `rejects TEMPLATE content with blank templateKey`() {
        val content = EmailContent(
            kind = EmailContentKind.TEMPLATE,
            templateKey = "",
            locale = "en-US",
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).hasSize(1)
        assertThat(errors.first().field).isEqualTo("content.templateKey")
        assertThat(errors.first().message).isEqualTo(EmailContentValidator.TEMPLATE_KEY_REQUIRED)
    }

    @Test
    fun `rejects TEMPLATE content with blank locale`() {
        val content = EmailContent(
            kind = EmailContentKind.TEMPLATE,
            templateKey = "welcome",
            locale = null,
        )

        val errors = EmailContentValidator.validate(content)

        assertThat(errors).hasSize(1)
        assertThat(errors.first().field).isEqualTo("content.locale")
        assertThat(errors.first().message).isEqualTo(EmailContentValidator.LOCALE_REQUIRED)
    }

    @Test
    fun `RAW kind is supported`() {
        assertThat(EmailContentValidator.isKindSupported(EmailContentKind.RAW)).isTrue()
    }

    @Test
    fun `TEMPLATE kind is not supported`() {
        assertThat(EmailContentValidator.isKindSupported(EmailContentKind.TEMPLATE)).isFalse()
    }
}
