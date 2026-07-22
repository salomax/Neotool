package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.model.dto.investment.ConfirmWithholdingTaxCommand
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.service.investment.ComeCotasSupport
import io.github.salomax.neotool.banking.service.investment.ConfirmWithholdingTaxService
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Come-cotas reconciliation integration")
class ComeCotasReconciliationIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var investmentTransactionService: InvestmentTransactionService

    @Inject
    lateinit var confirmWithholdingTaxService: ConfirmWithholdingTaxService

    @Inject
    lateinit var investmentTransactionRepository: InvestmentTransactionRepository

    @Inject
    lateinit var holdingRepository: HoldingRepository

    @Inject
    lateinit var portfolioFlowEventRepository: PortfolioFlowEventRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var assetId: UUID
    private lateinit var fundCnpj: String

    @BeforeEach
    fun setUpFixture() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        assetId = UUID.randomUUID()
        fundCnpj = "12${UUID.randomUUID().toString().filter(Char::isDigit).padEnd(12, '0').take(12)}"

        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at
                ) VALUES (
                    '$financialAccountId', '$accountId', '$userId', 'INVESTMENT', 'BROKERAGE', 'Broker', 'BRL', NOW(), NOW()
                )
                """.trimIndent(),
            )

            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id, name, asset_type, currency_code, fund_cnpj, created_at, updated_at
                ) VALUES (
                    '$assetId', 'FUNDO TESTE RF', '${AssetType.MUTUAL_FUND.name}', 'BRL', '$fundCnpj', NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `open finance withholding tax replaces synthetic come cotas without duplicating quota reduction`() {
        val purchaseDate = LocalDate.of(2026, 1, 2)
        val checkpointDate = LocalDate.of(2026, 5, 29)
        val importedTradeDate = checkpointDate.plusDays(1)

        investmentTransactionService.record(
            command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100.00000000"),
                    unitPrice = BigDecimal("1.00000000"),
                    grossValue = BigDecimal("100.00"),
                    netValue = BigDecimal("100.00"),
                    tradeDate = purchaseDate,
                    settlementDate = purchaseDate,
                    source = InvestmentSource.MANUAL,
                ),
            accountId = accountId,
            userId = userId,
        )

        val holding =
            requireNotNull(
                holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId),
            )

        confirmWithholdingTaxService.confirm(
            command =
                ConfirmWithholdingTaxCommand(
                    holdingId = requireNotNull(holding.id),
                    tradeDate = checkpointDate,
                    irValue = BigDecimal("15.00"),
                    quotasDeducted = BigDecimal("10.00000000"),
                    source = InvestmentSource.SYSTEM,
                    providerTransactionId = "comecotas:${holding.id}:$checkpointDate",
                ),
            accountId = accountId,
            userId = userId,
        )

        val holdingAfterSynthetic =
            requireNotNull(holdingRepository.findById(requireNotNull(holding.id), accountId))
        assertThat(holdingAfterSynthetic.quantity).isEqualByComparingTo("90.00000000")

        val importedWithholding =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = financialAccountId,
                        assetId = assetId,
                        direction = InvestmentTransactionDirection.OUTFLOW,
                        type = InvestmentTransactionType.WITHHOLDING_TAX,
                        quantity = BigDecimal("12.00000000"),
                        grossValue = BigDecimal("18.00"),
                        netValue = BigDecimal("18.00"),
                        incomeTax = BigDecimal("18.00"),
                        tradeDate = importedTradeDate,
                        settlementDate = importedTradeDate,
                        source = InvestmentSource.OPEN_FINANCE,
                        providerTransactionId = "ofb-comecotas-2026-05",
                    ),
                accountId = accountId,
                userId = userId,
            )

        val transactions =
            investmentTransactionRepository.findByHolding(
                holdingId = requireNotNull(holding.id),
                accountId = accountId,
            )

        assertThat(transactions).hasSize(2)
        assertThat(transactions.map { it.providerTransactionId })
            .containsExactlyInAnyOrder(null, "ofb-comecotas-2026-05")
        assertThat(transactions.count { it.type == InvestmentTransactionType.WITHHOLDING_TAX }).isEqualTo(1)
        assertThat(transactions.single { it.type == InvestmentTransactionType.WITHHOLDING_TAX }.source)
            .isEqualTo(InvestmentSource.OPEN_FINANCE)

        val finalHolding =
            requireNotNull(holdingRepository.findById(requireNotNull(holding.id), accountId))
        assertThat(finalHolding.quantity).isEqualByComparingTo("88.00000000")

        val checkpointEvents = portfolioFlowEventRepository.findByAccountAndDate(financialAccountId, checkpointDate)
        assertThat(checkpointEvents.none { it.eventType == PortfolioFlowEventType.INCOME_TAX }).isTrue()

        val importedEvents = portfolioFlowEventRepository.findByAccountAndDate(financialAccountId, importedTradeDate)
        assertThat(importedEvents.count { it.eventType == PortfolioFlowEventType.INCOME_TAX }).isEqualTo(1)
        val importedFlow = importedEvents.single { it.eventType == PortfolioFlowEventType.INCOME_TAX }
        assertThat(importedFlow.sourceReferenceId).isEqualTo(importedWithholding.id)
        assertThat(importedFlow.grossAmount).isEqualByComparingTo("18.00")
    }

    @Test
    fun `persisted withholding quantity reconstructs the expected surviving quota base`() {
        val purchaseDate = LocalDate.of(2025, 6, 2)
        val checkpointDate = LocalDate.of(2025, 11, 28)
        val expectedSurvivingQuantity = BigDecimal("756.00421069")

        investmentTransactionService.record(
            command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("765.07800000"),
                    unitPrice = BigDecimal("1.00000000"),
                    grossValue = BigDecimal("765.07800000"),
                    netValue = BigDecimal("765.07800000"),
                    tradeDate = purchaseDate,
                    settlementDate = purchaseDate,
                    source = InvestmentSource.MANUAL,
                ),
            accountId = accountId,
            userId = userId,
        )

        val holding =
            requireNotNull(
                holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId),
            )

        confirmWithholdingTaxService.confirm(
            command =
                ConfirmWithholdingTaxCommand(
                    holdingId = requireNotNull(holding.id),
                    tradeDate = checkpointDate,
                    irValue = BigDecimal("15.00"),
                    quotasDeducted = BigDecimal("9.07378931"),
                    source = InvestmentSource.SYSTEM,
                    providerTransactionId = "comecotas:${holding.id}:$checkpointDate",
                ),
            accountId = accountId,
            userId = userId,
        )

        val transactions =
            investmentTransactionRepository.findByHolding(
                holdingId = requireNotNull(holding.id),
                accountId = accountId,
            )
        val reconstructedQuantity =
            ComeCotasSupport.reconstructOpenLots(transactions)
                .fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.quantity) }

        assertThat(reconstructedQuantity.subtract(expectedSurvivingQuantity).abs())
            .isLessThan(BigDecimal("0.01"))
        assertThat(reconstructedQuantity).isEqualByComparingTo(expectedSurvivingQuantity)
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
