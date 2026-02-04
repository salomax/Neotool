package io.github.salomax.neotool.comms.template.registry.integration

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.registry.FileBasedTemplateRegistry
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Locale

@MicronautTest
@Tag("integration")
class FileBasedTemplateRegistryReloadIntegrationTest : TestPropertyProvider {
    @Inject
    lateinit var fileBasedTemplateRegistry: FileBasedTemplateRegistry

    override fun getProperties(): MutableMap<String, String> {
        return mutableMapOf(
            "comms.email.provider" to "mock",
            "datasources.default.enabled" to "false",
            "jpa.default.enabled" to "false",
            "flyway.enabled" to "false",
            "kafka.enabled" to "false",
            "comms.template.hot-reload" to "true",
        )
    }

    @Test
    fun `reload clears and repopulates cache`() {
        val before = fileBasedTemplateRegistry.resolve("user.welcome", Locale.ENGLISH, Channel.EMAIL)
        assertThat(before).isNotNull

        fileBasedTemplateRegistry.reload()

        val after = fileBasedTemplateRegistry.resolve("user.welcome", Locale.ENGLISH, Channel.EMAIL)
        assertThat(after).isNotNull
        assertThat(after!!.key).isEqualTo("user.welcome")
    }
}
