package io.github.salomax.neotool.banking.integration

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Investment holding schema IT")
class InvestmentHoldingSchemaIT : BankingPostgresIntegrationTest() {
    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var assetId: UUID

    @BeforeEach
    fun insertFixtures() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        assetId = UUID.randomUUID()

        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id,
                    account_id,
                    created_by_user_id,
                    type,
                    subtype,
                    name,
                    currency_code,
                    created_at,
                    updated_at
                ) VALUES (
                    '$financialAccountId',
                    '$accountId',
                    '$userId',
                    'INVESTMENT',
                    'BROKERAGE',
                    'Integration Broker',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )

            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id,
                    name,
                    asset_type,
                    currency_code,
                    created_at,
                    updated_at
                ) VALUES (
                    '$assetId',
                    'Schema Test Fund',
                    'MUTUAL_FUND',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `allows recreating the same holding after soft delete`() {
        openConnection().use { conn ->
            insertHolding(conn, assetId = assetId)

            conn.createStatement().executeUpdate(
                """
                UPDATE banking.investment_holdings
                   SET deleted_at = NOW(),
                       updated_at = NOW()
                 WHERE financial_account_id = '$financialAccountId'
                   AND asset_id = '$assetId'
                   AND deleted_at IS NULL
                """.trimIndent(),
            )

            val insertedRows = insertHolding(conn, assetId = assetId)
            assertThat(insertedRows).isEqualTo(1)

            conn.createStatement().executeQuery(
                """
                SELECT
                    COUNT(*) AS total_count,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS active_count
                FROM banking.investment_holdings
                WHERE financial_account_id = '$financialAccountId'
                  AND asset_id = '$assetId'
                """.trimIndent(),
            ).use { rs ->
                rs.next()
                assertThat(rs.getInt("total_count")).isEqualTo(2)
                assertThat(rs.getInt("active_count")).isEqualTo(1)
            }
        }
    }

    @Test
    fun `allows reusing provider investment id after soft delete`() {
        val providerInvestmentId = "provider-${UUID.randomUUID()}"

        openConnection().use { conn ->
            insertHolding(conn, assetId = assetId, providerInvestmentId = providerInvestmentId)

            conn.createStatement().executeUpdate(
                """
                UPDATE banking.investment_holdings
                   SET deleted_at = NOW(),
                       updated_at = NOW()
                 WHERE financial_account_id = '$financialAccountId'
                   AND provider_investment_id = '$providerInvestmentId'
                   AND deleted_at IS NULL
                """.trimIndent(),
            )

            val secondAssetId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id,
                    name,
                    asset_type,
                    currency_code,
                    created_at,
                    updated_at
                ) VALUES (
                    '$secondAssetId',
                    'Schema Test Provider Fund',
                    'MUTUAL_FUND',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )

            val insertedRows =
                insertHolding(
                    conn,
                    assetId = secondAssetId,
                    providerInvestmentId = providerInvestmentId,
                )
            assertThat(insertedRows).isEqualTo(1)

            conn.createStatement().executeQuery(
                """
                SELECT
                    COUNT(*) AS total_count,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS active_count
                FROM banking.investment_holdings
                WHERE financial_account_id = '$financialAccountId'
                  AND provider_investment_id = '$providerInvestmentId'
                """.trimIndent(),
            ).use { rs ->
                rs.next()
                assertThat(rs.getInt("total_count")).isEqualTo(2)
                assertThat(rs.getInt("active_count")).isEqualTo(1)
            }
        }
    }

    private fun insertHolding(
        conn: java.sql.Connection,
        assetId: UUID,
        providerInvestmentId: String? = null,
    ): Int {
        val holdingId = UUID.randomUUID()
        val providerValue = providerInvestmentId?.let { "'$it'" } ?: "NULL"
        return conn.createStatement().executeUpdate(
            """
            INSERT INTO banking.investment_holdings (
                id,
                financial_account_id,
                account_id,
                asset_id,
                quantity,
                average_cost,
                source,
                provider_investment_id,
                created_at,
                updated_at
            ) VALUES (
                '$holdingId',
                '$financialAccountId',
                '$accountId',
                '$assetId',
                10.00000000,
                100.00000000,
                'MANUAL',
                $providerValue,
                NOW(),
                NOW()
            )
            """.trimIndent(),
        )
    }

    private fun openConnection() =
        getPgContainer().let { container ->
            DriverManager.getConnection(
                buildJdbcUrlWithSslDisabled(container.jdbcUrl),
                container.username,
                container.password,
            )
        }
}
