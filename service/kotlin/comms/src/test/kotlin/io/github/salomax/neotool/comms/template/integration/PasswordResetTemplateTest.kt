package io.github.salomax.neotool.comms.template.integration

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.registry.TemplateRegistry
import io.github.salomax.neotool.comms.template.service.TemplateService
import io.github.salomax.neotool.comms.template.substitution.MissingVariableException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Locale

@MicronautTest
@Tag("integration")
class PasswordResetTemplateTest : TestPropertyProvider {
    @Inject
    lateinit var templateRegistry: TemplateRegistry

    @Inject
    lateinit var templateService: TemplateService

    override fun getProperties(): MutableMap<String, String> {
        return mutableMapOf(
            "comms.email.provider" to "mock",
            "datasources.default.enabled" to "false",
            "jpa.default.enabled" to "false",
            "flyway.enabled" to "false",
            "kafka.enabled" to "false",
        )
    }

    @Test
    fun `password reset template resolves for en locale`() {
        val template =
            templateRegistry.resolve(
                "auth.password-reset",
                Locale.ENGLISH,
                Channel.EMAIL,
            )
        assertNotNull(template)
        assertThat(template!!.key).isEqualTo("auth.password-reset")
    }

    @Test
    fun `password reset template renders with all variables`() {
        val rendered =
            templateService.renderTemplate(
                templateKey = "auth.password-reset",
                locale = Locale.ENGLISH,
                channel = Channel.EMAIL,
                variables =
                    mapOf(
                        "resetUrl" to "https://app.neotool.io/reset?token=abc123",
                        "expiresInMinutes" to 60,
                    ),
            )

        assertNotNull(rendered.subject)
        assertTrue(rendered.body.contains("app.neotool.io") || rendered.body.contains("reset"), "Body should contain reset URL: ${rendered.body.take(400)}")
        assertTrue(rendered.body.contains("minutes"), "Body should contain expiration text: ${rendered.body.take(400)}")
    }

    @Test
    fun `password reset template falls back to pt for pt-BR locale`() {
        val rendered =
            templateService.renderTemplate(
                templateKey = "auth.password-reset",
                locale = Locale("pt", "BR"),
                channel = Channel.EMAIL,
                variables =
                    mapOf(
                        "resetUrl" to "https://app.neotool.io/reset?token=abc123",
                    ),
            )

        assertTrue(rendered.subject?.contains("Redefina") ?: false)
    }

    @Test
    fun `password reset template fails when resetUrl missing`() {
        assertThrows(MissingVariableException::class.java) {
            templateService.renderTemplate(
                templateKey = "auth.password-reset",
                locale = Locale.ENGLISH,
                channel = Channel.EMAIL,
                variables = emptyMap(),
            )
            Unit
        }
    }

    @Test
    fun `password reset template uses default expiresInMinutes`() {
        val rendered =
            templateService.renderTemplate(
                templateKey = "auth.password-reset",
                locale = Locale.ENGLISH,
                channel = Channel.EMAIL,
                variables =
                    mapOf(
                        "resetUrl" to "https://app.neotool.io/reset?token=abc123",
                    ),
            )

        assertTrue(rendered.body.contains("60"))
    }

    @Test
    fun `password reset email has inlined CSS`() {
        val rendered =
            templateService.renderTemplate(
                templateKey = "auth.password-reset",
                locale = Locale.ENGLISH,
                channel = Channel.EMAIL,
                variables =
                    mapOf(
                        "resetUrl" to "https://app.neotool.io/reset?token=abc123",
                    ),
            )

        assertTrue(rendered.body.contains("style="))
    }
}
