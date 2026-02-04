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

class PushRendererTest {
    private val variableSubstitutor = VariableSubstitutor()
    private val renderer = PushRenderer(variableSubstitutor)

    @Test
    fun `should validate valid push template`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "push.test",
                channel = Channel.PUSH,
                variables = emptyList(),
                subjectTemplate = "Title",
                bodyTemplate = "Body",
            )
        val result = renderer.validate(template)
        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return validation errors when channel is not PUSH`() {
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
        assertThat(result.errors).contains("Template channel must be PUSH")
    }

    @Test
    fun `should return validation errors when subject is blank for locale`() {
        val template =
            TemplateDefinition(
                key = "test",
                channel = Channel.PUSH,
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
        assertThat(result.errors).anyMatch { it.contains("Subject") }
    }

    @Test
    fun `should render template with variables`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "push.hello",
                channel = Channel.PUSH,
                variables = listOf(VariableDefinition("name", VariableType.STRING, required = true)),
                subjectTemplate = "Hi {{name}}",
                bodyTemplate = "You have a notification",
            )
        val variables = mapOf<String, Any?>("name" to "Dave")
        val result = renderer.render(template, variables, Locale.ENGLISH)
        assertThat(result.channel).isEqualTo(Channel.PUSH)
        assertThat(result.subject).isEqualTo("Hi Dave")
        assertThat(result.body).contains("You have a notification")
        assertThat(result.metadata).containsEntry("platform", "ios")
    }

    @Test
    fun `should include badge and deepLink in push data when provided`() {
        val template =
            TemplateTestUtils.createTemplateWithVariables(
                key = "push.badge",
                channel = Channel.PUSH,
                variables = emptyList(),
                subjectTemplate = "Title",
                bodyTemplate = "Body",
            )
        val variables =
            mapOf<String, Any?>(
                "badgeCount" to 5,
                "deepLink" to "app://screen/1",
            )
        val result = renderer.render(template, variables, Locale.ENGLISH)
        assertThat(result.body).contains("badge")
        assertThat(result.body).contains("deepLink")
    }
}
