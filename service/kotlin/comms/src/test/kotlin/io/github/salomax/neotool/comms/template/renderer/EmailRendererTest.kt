package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateContent
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.domain.TemplateMetadata
import io.github.salomax.neotool.comms.template.domain.VariableDefinition
import io.github.salomax.neotool.comms.template.domain.VariableType
import io.github.salomax.neotool.comms.template.substitution.MissingVariableException
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import io.github.salomax.neotool.comms.template.test.TemplateTestUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Locale

class EmailRendererTest {
    private val variableSubstitutor = VariableSubstitutor()
    private val renderer = EmailRenderer(variableSubstitutor)

    @Test
    fun `should validate valid email template`() {
        val template = TemplateTestUtils.createTestEmailTemplate()
        val result = renderer.validate(template)
        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return validation errors when channel is not EMAIL`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "test",
                channel = Channel.CHAT,
                variables = emptyList(),
                subjectTemplate = "Subject",
                bodyTemplate = "Body",
            )
        val result = renderer.validate(template)
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).contains("Template channel must be EMAIL")
    }

    @Test
    fun `should return validation errors when subject is blank for locale`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.EMAIL,
                metadata = TemplateMetadata(name = "Test"),
                variables = emptyList(),
                locales =
                    mapOf(
                        Locale.ENGLISH to
                            TemplateContent(
                                locale = Locale.ENGLISH,
                                subject = "",
                                body = "Body",
                            ),
                    ),
                defaultLocale = Locale.ENGLISH,
            )
        val result = renderer.validate(template)
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.contains("Subject is required") }
    }

    @Test
    fun `should render template with variables`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "welcome",
                channel = Channel.EMAIL,
                variables = listOf(VariableDefinition("name", VariableType.STRING, required = true)),
                subjectTemplate = "Hello, {{name}}!",
                bodyTemplate = "<p>Welcome {{name}}</p>",
            )
        val variables = mapOf<String, Any?>("name" to "Alice")
        val result = renderer.render(template, variables, Locale.ENGLISH)
        assertThat(result.channel).isEqualTo(Channel.EMAIL)
        assertThat(result.subject).isEqualTo("Hello, Alice!")
        assertThat(result.body).contains("Welcome Alice")
        assertThat(result.metadata).containsEntry("format", "HTML")
    }

    @Test
    fun `should inline CSS when format is HTML`() {
        val htmlBody =
            """
            <style>.button { background: #007bff; color: white; }</style>
            <p class="button">Click me</p>
            """.trimIndent()
        val template =
            TemplateTestUtils.createEmailTemplateWithHtml(
                htmlBody = htmlBody,
                channelConfig = mapOf("format" to "HTML", "inlineCss" to true),
            )
        val result = renderer.render(template, emptyMap(), Locale.ENGLISH)
        assertThat(result.body).contains("background")
        assertThat(result.body).contains("Click me")
    }

    @Test
    fun `should add warning for image without alt text`() {
        val template =
            TemplateTestUtils.createEmailTemplateWithHtml(
                htmlBody = "<p>Text</p><img src=\"x.png\" />",
            )
        val result = renderer.validate(template)
        assertThat(result.warnings).anyMatch { it.contains("alt") }
    }

    @Test
    fun `should throw MissingVariableException when required variable is missing`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "welcome",
                channel = Channel.EMAIL,
                variables =
                    listOf(
                        VariableDefinition(
                            name = "name",
                            type = VariableType.STRING,
                            required = true,
                        ),
                    ),
                subjectTemplate = "Hello, {{name}}!",
                bodyTemplate = "<p>Welcome {{name}}</p>",
            )
        val variables = emptyMap<String, Any?>()

        assertThatThrownBy { renderer.render(template, variables, Locale.ENGLISH) }
            .isInstanceOf(MissingVariableException::class.java)
            .hasMessageContaining("name")
    }
}
