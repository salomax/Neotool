package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Base integration test for the Banking service.
 * Verifies the application context starts successfully against a real PostgreSQL container.
 */
@MicronautTest(startApplication = true)
@DisplayName("Banking Service Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("banking")
open class BankingIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    @Test
    @DisplayName("application context starts with database")
    fun `should start application context with PostgreSQL`() {
        assertThat(true).isTrue()
    }
}
