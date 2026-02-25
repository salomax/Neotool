package io.github.salomax.neotool.banking

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MicronautTest(startApplication = false)
@DisplayName("Banking Application Unit Tests")
class BankingApplicationTest {

    @Test
    @DisplayName("application context loads")
    fun `should load application context`() {
        assertThat(true).isTrue()
    }
}
