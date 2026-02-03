package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.VariableDefinition
import io.github.salomax.neotool.comms.template.domain.VariableType
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import io.github.salomax.neotool.comms.template.test.TemplateTestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class ChatRendererTest {
    private val variableSubstitutor = VariableSubstitutor()
    private val renderer = ChatRenderer(variableSubstitutor)

    @Test
    fun `should validate valid chat template`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "chat.test",
                channel = Channel.CHAT,
                variables = emptyList(),
                subjectTemplate = "",
                bodyTemplate = "Short message",
            )
        val result = renderer.validate(template)
        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return validation errors when channel is not CHAT`() {
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
        assertThat(result.errors).contains("Template channel must be CHAT")
    }

    @Test
    fun `should render template with variables`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "chat.hello",
                channel = Channel.CHAT,
                variables = listOf(VariableDefinition("user", VariableType.STRING, required = true)),
                subjectTemplate = "",
                bodyTemplate = "Hi {{user}}, welcome!",
            )
        val variables = mapOf<String, Any?>("user" to "Bob")
        val result = renderer.render(template, variables, Locale.ENGLISH)
        assertThat(result.channel).isEqualTo(Channel.CHAT)
        assertThat(result.subject).isNull()
        assertThat(result.body).isEqualTo("Hi Bob, welcome!")
        assertThat(result.metadata).containsKey("length")
    }

    @Test
    fun `should truncate body when exceeding max message length`() {
        val longBody = "x".repeat(5000)
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "chat.long",
                channel = Channel.CHAT,
                variables = emptyList(),
                subjectTemplate = "",
                bodyTemplate = longBody,
            )
        val result = renderer.render(template, emptyMap(), Locale.ENGLISH)
        assertThat(result.body).hasSize(4000)
    }
}
