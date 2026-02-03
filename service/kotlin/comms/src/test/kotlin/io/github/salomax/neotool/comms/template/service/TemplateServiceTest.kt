package io.github.salomax.neotool.comms.template.service

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.registry.TemplateNotFoundException
import io.github.salomax.neotool.comms.template.registry.TemplateRegistry
import io.github.salomax.neotool.comms.template.renderer.RenderedTemplate
import io.github.salomax.neotool.comms.template.renderer.TemplateRenderer
import io.github.salomax.neotool.comms.template.renderer.TemplateRendererFactory
import io.github.salomax.neotool.comms.template.test.TemplateTestUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Locale

class TemplateServiceTest {
    private val templateRegistry = mockk<TemplateRegistry>()
    private val rendererFactory = mockk<TemplateRendererFactory>()
    private val renderer = mockk<TemplateRenderer>()
    private val service = TemplateService(templateRegistry, rendererFactory)

    @Test
    fun `renderTemplate returns rendered result when template found`() {
        val template = TemplateTestUtils.createTestEmailTemplate()
        val variables = mapOf<String, Any?>()
        val expected =
            RenderedTemplate(
                channel = Channel.EMAIL,
                subject = "Test",
                body = "Body",
                metadata = emptyMap(),
            )
        every { templateRegistry.resolve("user.welcome", Locale.ENGLISH, Channel.EMAIL) } returns template
        every { rendererFactory.getRenderer(Channel.EMAIL) } returns renderer
        every { renderer.render(template, variables, Locale.ENGLISH) } returns expected

        val result = service.renderTemplate("user.welcome", Locale.ENGLISH, Channel.EMAIL, variables)

        assertThat(result).isEqualTo(expected)
        verify(exactly = 1) { templateRegistry.resolve("user.welcome", Locale.ENGLISH, Channel.EMAIL) }
        verify(exactly = 1) { renderer.render(template, variables, Locale.ENGLISH) }
    }

    @Test
    fun `renderTemplate throws TemplateNotFoundException when template not found`() {
        every { templateRegistry.resolve("missing", Locale.ENGLISH, Channel.EMAIL) } returns null

        assertThatThrownBy {
            service.renderTemplate("missing", Locale.ENGLISH, Channel.EMAIL, emptyMap())
        }
            .isInstanceOf(TemplateNotFoundException::class.java)
            .hasMessageContaining("missing")
            .hasMessageContaining("EMAIL")

        verify(exactly = 1) { templateRegistry.resolve("missing", Locale.ENGLISH, Channel.EMAIL) }
        verify(exactly = 0) { rendererFactory.getRenderer(any()) }
    }

    @Test
    fun `getTemplate returns template when found`() {
        val template = TemplateTestUtils.createTestEmailTemplate()
        val defaultLocale = Locale.getDefault()
        every { templateRegistry.resolve("user.welcome", defaultLocale, Channel.EMAIL) } returns template

        val result = service.getTemplate("user.welcome", Channel.EMAIL)

        assertThat(result).isEqualTo(template)
        verify(exactly = 1) { templateRegistry.resolve("user.welcome", defaultLocale, Channel.EMAIL) }
    }

    @Test
    fun `getTemplate returns null when template not found`() {
        val defaultLocale = Locale.getDefault()
        every { templateRegistry.resolve("missing", defaultLocale, Channel.EMAIL) } returns null

        val result = service.getTemplate("missing", Channel.EMAIL)

        assertThat(result).isNull()
    }

    @Test
    fun `listTemplates returns list from registry`() {
        val templates =
            listOf(
                TemplateTestUtils.createTestEmailTemplate("a"),
                TemplateTestUtils.createTestEmailTemplate("b"),
            )
        every { templateRegistry.listByChannel(Channel.EMAIL) } returns templates

        val result = service.listTemplates(Channel.EMAIL)

        assertThat(result).isEqualTo(templates)
        verify(exactly = 1) { templateRegistry.listByChannel(Channel.EMAIL) }
    }
}
