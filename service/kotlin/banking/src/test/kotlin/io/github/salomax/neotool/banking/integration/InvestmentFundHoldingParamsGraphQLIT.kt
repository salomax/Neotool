package io.github.salomax.neotool.banking.integration

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.service.investment.InvestmentAssetService
import io.github.salomax.neotool.banking.service.investment.InvestmentQueryService
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.shared.BankingPermissions
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Investment fund holding params GraphQL IT")
class InvestmentFundHoldingParamsGraphQLIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var graphQL: GraphQL

    @Inject
    lateinit var financialAccountService: FinancialAccountService

    @Inject
    lateinit var investmentAssetService: InvestmentAssetService

    @Inject
    lateinit var investmentTransactionService: InvestmentTransactionService

    @Inject
    lateinit var investmentQueryService: InvestmentQueryService

    @Inject
    lateinit var portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID

    @BeforeEach
    override fun setUp() {
        super.setUp()
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId =
            requireNotNull(
                financialAccountService.create(
                    command =
                        CreateFinancialAccountCommand(
                            type = FinancialAccountType.INVESTMENT,
                            subtype = FinancialAccountSubtype.BROKERAGE,
                            name = "GraphQL IT Broker",
                            issuerName = "GraphQL IT Issuer",
                        ),
                    accountId = accountId,
                    userId = userId,
                ).id,
            )
    }

    @Test
    fun `recordInvestmentTransaction persists administration fee through graphql mutation`() {
        val asset =
            investmentAssetService.create(
                name = "GraphQL IT Fund",
                assetType = AssetType.MUTUAL_FUND,
                issuerName = null,
                fundCnpj = null,
                ticker = null,
                isinCode = null,
                currencyCode = "BRL",
            )
        val tradeDate = LocalDate.of(2026, 6, 10)

        val result =
            executeMutation(
                query =
                    """
                    mutation RecordInvestmentTransaction(${'$'}input: RecordInvestmentTransactionInput!) {
                      recordInvestmentTransaction(input: ${'$'}input) {
                        id
                        holdingId
                        financialAccountId
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "input" to
                            mapOf(
                                "financialAccountId" to financialAccountId.toString(),
                                "assetId" to requireNotNull(asset.id).toString(),
                                "direction" to InvestmentTransactionDirection.INFLOW.name,
                                "type" to InvestmentTransactionType.PURCHASE.name,
                                "tradeDate" to tradeDate.toString(),
                                "settlementDate" to tradeDate.toString(),
                                "quantity" to 100.0,
                                "unitPrice" to 10.0,
                                "grossValue" to 1000.0,
                                "netValue" to 1000.0,
                                "pricingMode" to HoldingPricingMode.MARKET_DATA.name,
                                "administrationFeeAnnualPercent" to 1.75,
                            ),
                    ),
            )

        assertThat(result.getErrors()).isEmpty()
        val payload = result.dataMap()["recordInvestmentTransaction"] as Map<*, *>
        val holdingId = UUID.fromString(payload["holdingId"] as String)
        val holding = requireNotNull(investmentQueryService.getHolding(holdingId, accountId))
        val latestJob = portfolioRecalculationJobRepository.findLatestByAccount(accountId)

        assertThat(payload["financialAccountId"]).isEqualTo(financialAccountId.toString())
        assertThat(holding.administrationFeeAnnualPercent).isEqualTo(1.75)
        assertThat(holding.pricingMode).isEqualTo(HoldingPricingMode.MARKET_DATA)
        assertThat(latestJob).isNotNull()
        assertThat(latestJob!!.financialAccountId).isEqualTo(financialAccountId)
        assertThat(latestJob.recalculateFrom).isEqualTo(tradeDate)
    }

    @Test
    fun `updateFundHoldingParams updates persisted overrides through graphql mutation`() {
        val asset =
            investmentAssetService.create(
                name = "GraphQL IT Editable Fund",
                assetType = AssetType.MUTUAL_FUND,
                issuerName = null,
                fundCnpj = null,
                ticker = null,
                isinCode = null,
                currencyCode = "BRL",
            )
        val tradeDate = LocalDate.of(2026, 5, 10)
        val recorded =
            investmentTransactionService.record(
                command =
                    RecordInvestmentTransactionCommand(
                        financialAccountId = financialAccountId,
                        assetId = requireNotNull(asset.id),
                        direction = InvestmentTransactionDirection.INFLOW,
                        type = InvestmentTransactionType.PURCHASE,
                        quantity = BigDecimal("100"),
                        unitPrice = BigDecimal("10"),
                        grossValue = BigDecimal("1000"),
                        netValue = BigDecimal("1000"),
                        tradeDate = tradeDate,
                        settlementDate = tradeDate,
                        pricingMode = HoldingPricingMode.MARKET_DATA,
                        administrationFeeAnnualPercent = BigDecimal("1.25"),
                        source = InvestmentSource.MANUAL,
                    ),
                accountId = accountId,
                userId = userId,
            )

        val result =
            executeMutation(
                query =
                    """
                    mutation UpdateFundHoldingParams(${'$'}input: UpdateFundHoldingParamsInput!) {
                      updateFundHoldingParams(input: ${'$'}input) {
                        id
                        comeCotasEnabled
                        administrationFeeAnnualPercent
                      }
                    }
                    """.trimIndent(),
                variables =
                    mapOf(
                        "input" to
                            mapOf(
                                "holdingId" to recorded.holdingId.toString(),
                                "comeCotasEnabled" to true,
                                "administrationFeeAnnualPercent" to 2.25,
                            ),
                    ),
            )

        assertThat(result.getErrors()).isEmpty()
        val payload = result.dataMap()["updateFundHoldingParams"] as Map<*, *>
        val holding = requireNotNull(investmentQueryService.getHolding(recorded.holdingId, accountId))
        val latestJob = portfolioRecalculationJobRepository.findLatestByAccount(accountId)

        assertThat(payload["comeCotasEnabled"]).isEqualTo(true)
        assertThat(payload["administrationFeeAnnualPercent"]).isEqualTo(2.25)
        assertThat(holding.comeCotasEnabled).isTrue()
        assertThat(holding.administrationFeeAnnualPercent).isEqualTo(2.25)
        assertThat(latestJob).isNotNull()
        assertThat(latestJob!!.triggeredBy).startsWith("fund_holding_params:${recorded.holdingId}")
        assertThat(latestJob.recalculateFrom).isEqualTo(tradeDate)
    }

    private fun executeMutation(
        query: String,
        variables: Map<String, Any?>,
    ) = graphQL.execute(
        ExecutionInput
            .newExecutionInput()
            .query(query)
            .variables(variables)
            .graphQLContext(mapOf("requestPrincipal" to principal()))
            .build(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun ExecutionResult.dataMap(): Map<String, Any?> = getData() as Map<String, Any?>

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = listOf(BankingPermissions.BANKING_INVESTMENT_UPDATE),
            currentAccountId = accountId,
        )
}
