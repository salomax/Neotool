package io.github.salomax.neotool.common.featureflags

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UnleashConfig Unit Tests")
class UnleashConfigTest {
    @Test
    fun `should apply default values`() {
        val config = UnleashConfig(url = "http://unleash", appName = "test-app")

        assertThat(config.url).isEqualTo("http://unleash")
        assertThat(config.appName).isEqualTo("test-app")
        assertThat(config.apiToken).isNull()
        assertThat(config.instanceId).isNull()
        assertThat(config.refreshInterval).isEqualTo(15)
        assertThat(config.disableMetrics).isFalse
        assertThat(config.disableAutoStart).isFalse
    }
}
