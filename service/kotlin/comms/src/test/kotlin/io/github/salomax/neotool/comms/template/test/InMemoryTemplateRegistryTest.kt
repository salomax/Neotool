package io.github.salomax.neotool.comms.template.test

import io.github.salomax.neotool.comms.template.domain.Channel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class InMemoryTemplateRegistryTest {
    @Test
    fun `register and resolve returns template when found`() {
        val registry = InMemoryTemplateRegistry()
        val template = TemplateTestUtils.createTestEmailTemplate("welcome")
        registry.register(template)

        val result = registry.resolve("welcome", Locale.ENGLISH, Channel.EMAIL)

        assertThat(result).isNotNull
        assertThat(result!!.key).isEqualTo("welcome")
        assertThat(result.channel).isEqualTo(Channel.EMAIL)
    }

    @Test
    fun `resolve returns null when key not found`() {
        val registry = InMemoryTemplateRegistry()
        registry.register(TemplateTestUtils.createTestEmailTemplate("welcome"))

        val result = registry.resolve("missing", Locale.ENGLISH, Channel.EMAIL)

        assertThat(result).isNull()
    }

    @Test
    fun `resolve returns null when channel has no templates`() {
        val registry = InMemoryTemplateRegistry()
        registry.register(TemplateTestUtils.createTestEmailTemplate("welcome"))

        val result = registry.resolve("welcome", Locale.ENGLISH, Channel.CHAT)

        assertThat(result).isNull()
    }

    @Test
    fun `resolve returns template with default locale when requested locale not in template`() {
        val registry = InMemoryTemplateRegistry()
        val template = TemplateTestUtils.createTestEmailTemplate("welcome")
        registry.register(template)

        val result = registry.resolve("welcome", Locale.GERMAN, Channel.EMAIL)

        assertThat(result).isNotNull
        assertThat(result!!.key).isEqualTo("welcome")
        assertThat(result.defaultLocale).isEqualTo(Locale.ENGLISH)
    }

    @Test
    fun `resolve returns template with locale fallback`() {
        val registry = InMemoryTemplateRegistry()
        val template = TemplateTestUtils.createTestEmailTemplate("welcome")
        registry.register(template)

        val result = registry.resolve("welcome", Locale("pt", "BR"), Channel.EMAIL)

        assertThat(result).isNotNull
        assertThat(result!!.key).isEqualTo("welcome")
    }

    @Test
    fun `listByChannel returns registered templates for channel`() {
        val registry = InMemoryTemplateRegistry()
        val t1 = TemplateTestUtils.createTestEmailTemplate("a")
        val t2 = TemplateTestUtils.createTestEmailTemplate("b")
        registry.register(t1)
        registry.register(t2)

        val list = registry.listByChannel(Channel.EMAIL)

        assertThat(list).hasSize(2)
        assertThat(list.map { it.key }).containsExactlyInAnyOrder("a", "b")
    }

    @Test
    fun `listByChannel returns empty list when channel has no templates`() {
        val registry = InMemoryTemplateRegistry()
        registry.register(TemplateTestUtils.createTestEmailTemplate("welcome"))

        val list = registry.listByChannel(Channel.CHAT)

        assertThat(list).isEmpty()
    }

    @Test
    fun `clear removes all templates`() {
        val registry = InMemoryTemplateRegistry()
        registry.register(TemplateTestUtils.createTestEmailTemplate("welcome"))

        registry.clear()

        assertThat(registry.resolve("welcome", Locale.ENGLISH, Channel.EMAIL)).isNull()
        assertThat(registry.listByChannel(Channel.EMAIL)).isEmpty()
    }

    @Test
    fun `reload does not throw`() {
        val registry = InMemoryTemplateRegistry()
        registry.register(TemplateTestUtils.createTestEmailTemplate("welcome"))

        registry.reload()

        assertThat(registry.resolve("welcome", Locale.ENGLISH, Channel.EMAIL)).isNotNull
    }
}
