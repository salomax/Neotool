package io.github.salomax.neotool.banking.integration

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.DriverManager

@MicronautTest(startApplication = true)
@DisplayName("IssuerCategory migration IT")
class IssuerCategoryMigrationIT : BankingPostgresIntegrationTest() {
    @Nested
    @DisplayName("schema")
    inner class SchemaTests {
        @Test
        fun `issuers category column is non-null text with BANK default`() {
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeQuery(
                    """
                    SELECT is_nullable, data_type, column_default
                    FROM information_schema.columns
                    WHERE table_schema = 'banking'
                      AND table_name = 'issuers'
                      AND column_name = 'category'
                    """.trimIndent(),
                ).use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getString("is_nullable")).isEqualTo("NO")
                    assertThat(rs.getString("data_type")).isEqualTo("text")
                    assertThat(rs.getString("column_default")).contains("BANK")
                }
            }
        }
    }

    @Nested
    @DisplayName("data")
    inner class DataTests {
        @Test
        fun `all issuers are categorized and crypto exchanges are inserted`() {
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeQuery(
                    "SELECT COUNT(*) AS cnt FROM banking.issuers WHERE category IS NULL",
                ).use { rs ->
                    rs.next()
                    assertThat(rs.getInt("cnt")).isEqualTo(0)
                }

                conn.createStatement().executeQuery(
                    """
                    SELECT name, category
                    FROM banking.issuers
                    WHERE name IN ('Binance', 'Coinbase', 'Kraken', 'Bybit', 'OKX')
                    ORDER BY name
                    """.trimIndent(),
                ).use { rs ->
                    val rows = mutableMapOf<String, String>()
                    while (rs.next()) {
                        rows[rs.getString("name")] = rs.getString("category")
                    }
                    assertThat(rows).hasSize(5)
                    assertThat(rows.values).allMatch { it == "CRYPTO_EXCHANGE" }
                }
            }
        }
    }
}
