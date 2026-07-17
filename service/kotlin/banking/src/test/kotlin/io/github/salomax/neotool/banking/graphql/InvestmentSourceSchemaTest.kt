package io.github.salomax.neotool.banking.graphql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("InvestmentSource schema")
class InvestmentSourceSchemaTest {
    @Test
    fun `runtime schema exposes SYSTEM enum value`() {
        val schema =
            requireNotNull(javaClass.getResource("/graphql/schema.graphqls")) {
                "GraphQL runtime schema resource not found"
            }.readText()

        val investmentSourceEnum =
            Regex("""enum\s+InvestmentSource\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)
                .find(schema)
                ?.groupValues
                ?.getOrNull(1)

        assertThat(investmentSourceEnum).isNotNull
        assertThat(investmentSourceEnum).contains("SYSTEM")
    }
}
