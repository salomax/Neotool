package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.service.investment.InvestmentPortfolioSummaryService
import io.github.salomax.neotool.banking.service.investment.InvestmentQueryService
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Investment portfolio summary fund integration")
class InvestmentPortfolioSummaryFundIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var investmentTransactionService: InvestmentTransactionService

    @Inject
    lateinit var investmentQueryService: InvestmentQueryService

    @Inject
    lateinit var investmentPortfolioSummaryService: InvestmentPortfolioSummaryService

    @Inject
    lateinit var holdingRepository: HoldingRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var fundAssetId: UUID

    @BeforeEach
    fun setUpFixture() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        fundAssetId = UUID.randomUUID()
        val fundCnpj = fundAssetId.toString().filter(Char::isDigit).padEnd(14, '0').take(14)

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
                    'Summary Broker',
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
                    fund_cnpj,
                    currency_code,
                    created_at,
                    updated_at
                ) VALUES (
                    '$fundAssetId',
                    'Summary Test Fund',
                    'MUTUAL_FUND',
                    '$fundCnpj',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `fund summary keeps total portfolio value separate from gross gain`() {
        investmentTransactionService.record(
            RecordInvestmentTransactionCommand(
                financialAccountId = financialAccountId,
                assetId = fundAssetId,
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100.00000000"),
                unitPrice = BigDecimal("10.00000000"),
                grossValue = BigDecimal("1000.00"),
                tradeDate = java.time.LocalDate.now().minusDays(2),
                settlementDate = java.time.LocalDate.now().minusDays(2),
            ),
            accountId = accountId,
            userId = userId,
        )

        val holding =
            requireNotNull(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, fundAssetId)) {
                "Holding should exist after recording the fund purchase"
            }
        holdingRepository.update(
            holding.copy(
                grossValue = BigDecimal("1050.00"),
                netValue = BigDecimal("1050.00"),
            ),
        )

        val holdings =
            investmentQueryService.getHoldings(
                financialAccountId = financialAccountId,
                accountId = accountId,
            )
        val summary =
            investmentPortfolioSummaryService.getSummary(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
            )

        assertThat(holdings).hasSize(1)
        assertThat(holdings.first().grossValue).isEqualTo(1050.0)
        assertThat(summary.totalCostBasis).isEqualTo(1000.0)
        assertThat(summary.grossGain).isEqualTo(50.0)
        assertThat(summary.totalPortfolioValue).isEqualTo(1050.0)
        assertThat(summary.totalPortfolioValue).isNotEqualTo(summary.grossGain)
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
