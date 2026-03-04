package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance

/**
 * Base for banking integration tests that need PostgreSQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("banking")
abstract class BankingPostgresIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()
        props["datasources.default.url"] = buildJdbcUrlWithSslDisabled(props.getValue("datasources.default.url"))
        return props
    }

    protected fun buildJdbcUrlWithSslDisabled(baseUrl: String): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}sslmode=disable"
    }
}
