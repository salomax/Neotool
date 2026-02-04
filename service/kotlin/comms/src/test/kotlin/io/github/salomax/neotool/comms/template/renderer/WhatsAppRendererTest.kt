package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.VariableDefinition
import io.github.salomax.neotool.comms.template.domain.VariableType
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import io.github.salomax.neotool.comms.template.test.TemplateTestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class WhatsAppRendererTest {
    private val variableSubstitutor = VariableSubstitutor()
    private val renderer = WhatsAppRenderer(variableSubstitutor)

    @Test
    fun `should validate valid WhatsApp template`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "wa.test",
                channel = Channel.WHATSAPP,
                variables = emptyList(),
                subjectTemplate = "",
                bodyTemplate = "Short message",
            )
        val result = renderer.validate(template)
        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return validation errors when channel is not WHATSAPP`() {
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
        assertThat(result.errors).contains("Template channel must be WHATSAPP")
    }

    @Test
    fun `should render template with variables`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "wa.hello",
                channel = Channel.WHATSAPP,
                variables = listOf(VariableDefinition("name", VariableType.STRING, required = true)),
                subjectTemplate = "",
                bodyTemplate = "Hi {{name}}, welcome to WhatsApp!",
            )
        val variables = mapOf<String, Any?>("name" to "Eve")
        val result = renderer.render(template, variables, Locale.ENGLISH)
        assertThat(result.channel).isEqualTo(Channel.WHATSAPP)
        assertThat(result.subject).isNull()
        assertThat(result.body).isEqualTo("Hi Eve, welcome to WhatsApp!")
        assertThat(result.metadata).containsKey("length")
    }

    @Test
    fun `should truncate body when exceeding WhatsApp character limit`() {
        val longBody = "x".repeat(5000)
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "wa.long",
                channel = Channel.WHATSAPP,
                variables = emptyList(),
                subjectTemplate = "",
                bodyTemplate = longBody,
            )
        val result = renderer.render(template, emptyMap(), Locale.ENGLISH)
        assertThat(result.body).hasSize(4096)
    }
}
