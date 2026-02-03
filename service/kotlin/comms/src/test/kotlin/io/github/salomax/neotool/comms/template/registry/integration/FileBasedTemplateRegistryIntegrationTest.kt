package io.github.salomax.neotool.comms.template.registry.integration

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.registry.TemplateRegistry
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Locale

@MicronautTest
@Tag("integration")
class FileBasedTemplateRegistryIntegrationTest : TestPropertyProvider {
    @Inject
    lateinit var templateRegistry: TemplateRegistry

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
    fun `resolve returns user-welcome template for EMAIL channel`() {
        val template = templateRegistry.resolve("user.welcome", Locale.ENGLISH, Channel.EMAIL)
        assertThat(template).isNotNull
        assertThat(template!!.key).isEqualTo("user.welcome")
        assertThat(template.channel).isEqualTo(Channel.EMAIL)
        assertThat(template.locales).isNotEmpty
    }

    @Test
    fun `resolve returns null for unknown key`() {
        val template = templateRegistry.resolve("unknown-template", Locale.ENGLISH, Channel.EMAIL)
        assertThat(template).isNull()
    }

    @Test
    fun `listByChannel returns templates for EMAIL channel`() {
        val templates = templateRegistry.listByChannel(Channel.EMAIL)
        assertThat(templates).isNotNull
        assertThat(templates.any { it.key == "user.welcome" }).isTrue
    }

    @Test
    fun `listByChannel returns empty list for channel with no templates`() {
        val templates = templateRegistry.listByChannel(Channel.CHAT)
        assertThat(templates).isEmpty()
    }
}
