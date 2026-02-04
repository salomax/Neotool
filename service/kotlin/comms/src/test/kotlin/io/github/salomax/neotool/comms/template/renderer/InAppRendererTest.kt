package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateContent
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.domain.TemplateMetadata
import io.github.salomax.neotool.comms.template.domain.VariableDefinition
import io.github.salomax.neotool.comms.template.domain.VariableType
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import io.github.salomax.neotool.comms.template.test.TemplateTestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class InAppRendererTest {
    private val variableSubstitutor = VariableSubstitutor()
    private val renderer = InAppRenderer(variableSubstitutor)

    @Test
    fun `should validate valid in-app template`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "inapp.test",
                channel = Channel.IN_APP,
                variables = emptyList(),
                subjectTemplate = "Title",
                bodyTemplate = "Body text",
            )
        val result = renderer.validate(template)
        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return validation errors when channel is not IN_APP`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "test",
                channel = Channel.EMAIL,
                variables = emptyList(),
                subjectTemplate = "Subject",
                bodyTemplate = "Body",
            )
        val result = renderer.validate(template)
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).contains("Template channel must be IN_APP")
    }

    @Test
    fun `should add warning for potential XSS when body contains script tag`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.IN_APP,
                metadata = TemplateMetadata(name = "Test"),
                variables = emptyList(),
                locales =
                    mapOf(
                        Locale.ENGLISH to
                            TemplateContent(
                                locale = Locale.ENGLISH,
                                subject = "Title",
                                body = "<script>alert(1)</script>",
                            ),
                    ),
                defaultLocale = Locale.ENGLISH,
            )
        val result = renderer.validate(template)
        assertThat(result.warnings).anyMatch { it.contains("XSS") }
    }

    @Test
    fun `should render template with variables`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "inapp.hello",
                channel = Channel.IN_APP,
                variables = listOf(VariableDefinition("name", VariableType.STRING, required = true)),
                subjectTemplate = "Hello {{name}}",
                bodyTemplate = "Welcome {{name}}!",
            )
        val variables = mapOf<String, Any?>("name" to "Carol")
        val result = renderer.render(template, variables, Locale.ENGLISH)
        assertThat(result.channel).isEqualTo(Channel.IN_APP)
        assertThat(result.subject).isEqualTo("Hello Carol")
        assertThat(result.body).contains("Welcome Carol")
        assertThat(result.metadata).containsKey("actionButtons")
    }

    @Test
    fun `should include action buttons when variables provided`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "inapp.actions",
                channel = Channel.IN_APP,
                variables = emptyList(),
                subjectTemplate = "Title",
                bodyTemplate = "Body",
            )
        val variables =
            mapOf<String, Any?>(
                "actionButton1Text" to "Click",
                "actionButton1Url" to "https://example.com",
            )
        val result = renderer.render(template, variables, Locale.ENGLISH)
        assertThat(result.body).contains("Click")
        assertThat(result.body).contains("https://example.com")
    }
}
