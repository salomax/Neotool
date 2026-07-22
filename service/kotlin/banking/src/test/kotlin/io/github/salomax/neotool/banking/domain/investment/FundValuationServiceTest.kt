package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.service.investment.FundValuationService
import io.github.salomax.neotool.banking.service.investment.ValuationSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("FundValuationService")
class FundValuationServiceTest {
    private val service = FundValuationService()

    @Test
    fun `market-data fund returns gross value with estimated IR and IOF`() {
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("1.00"),
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = "12345678000199",
                assetType = "MUTUAL_FUND",
                taxProfile = FundTaxProfile.COME_COTAS_LONG,
                latestNav =
                    FundMarketNav(
                        date = LocalDate.of(2025, 12, 1),
                        closePrice = BigDecimal("1.15"),
                    ),
            )
        val transactions =
            listOf(
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holding.id!!,
                    financialAccountId = holding.financialAccountId,
                    accountId = holding.accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("1.00"),
                    grossValue = BigDecimal("100.00"),
                    tradeDate = LocalDate.of(2025, 1, 2),
                    createdAt = Instant.parse("2025-01-02T12:00:00Z"),
                    updatedAt = Instant.parse("2025-01-02T12:00:00Z"),
                ),
            )

        val result = service.estimate(holding, snapshot, transactions)

        assertThat(result.valuationSource).isEqualTo(ValuationSource.MARKET_DATA)
        assertThat(result.valuationReferenceDate).isEqualTo(LocalDate.of(2025, 12, 1))
        assertThat(result.grossValue).isEqualByComparingTo("115.00")
        assertThat(result.incomeTax).isNotNull
        assertThat(result.incomeTax).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        assertThat(result.iof).isEqualByComparingTo("0")
        assertThat(result.netValue).isNotNull
        assertThat(result.netValue).isLessThan(result.grossValue)
    }

    @Test
    fun `unknown tax profile returns gross value only`() {
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal("25"),
                averageCost = BigDecimal("10.00"),
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = "12345678000199",
                assetType = "CAMBIAL",
                taxProfile = FundTaxProfile.UNKNOWN,
                latestNav =
                    FundMarketNav(
                        date = LocalDate.of(2025, 8, 12),
                        closePrice = BigDecimal("12.00"),
                    ),
            )

        val result = service.estimate(holding, snapshot, emptyList())

        assertThat(result.grossValue).isEqualByComparingTo("300.00")
        assertThat(result.incomeTax).isNull()
        assertThat(result.iof).isNull()
        assertThat(result.netValue).isNull()
    }

    @Test
    fun `withholding tax only offsets the lots that remain open`() {
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal("50"),
                averageCost = BigDecimal("1.00"),
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = "12345678000199",
                assetType = "EQUITY_FUND",
                taxProfile = FundTaxProfile.VARIABLE_INCOME_15,
                latestNav =
                    FundMarketNav(
                        date = LocalDate.of(2025, 12, 1),
                        closePrice = BigDecimal("1.80"),
                    ),
            )
        val transactions =
            listOf(
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holding.id!!,
                    financialAccountId = holding.financialAccountId,
                    accountId = holding.accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("1.00"),
                    grossValue = BigDecimal("100.00"),
                    tradeDate = LocalDate.of(2025, 1, 2),
                    createdAt = Instant.parse("2025-01-02T12:00:00Z"),
                    updatedAt = Instant.parse("2025-01-02T12:00:00Z"),
                ),
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holding.id,
                    financialAccountId = holding.financialAccountId,
                    accountId = holding.accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.WITHHOLDING_TAX,
                    grossValue = BigDecimal("4.00"),
                    tradeDate = LocalDate.of(2025, 6, 2),
                    createdAt = Instant.parse("2025-06-02T12:00:00Z"),
                    updatedAt = Instant.parse("2025-06-02T12:00:00Z"),
                ),
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holding.id,
                    financialAccountId = holding.financialAccountId,
                    accountId = holding.accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.SALE,
                    quantity = BigDecimal("50"),
                    grossValue = BigDecimal("90.00"),
                    tradeDate = LocalDate.of(2025, 7, 1),
                    createdAt = Instant.parse("2025-07-01T12:00:00Z"),
                    updatedAt = Instant.parse("2025-07-01T12:00:00Z"),
                ),
            )

        val result = service.estimate(holding, snapshot, transactions)

        assertThat(result.grossValue).isEqualByComparingTo("90.00")
        assertThat(result.iof).isEqualByComparingTo("0")
        assertThat(result.incomeTax).isEqualByComparingTo("4.00")
        assertThat(result.netValue).isEqualByComparingTo("86.00")
    }

    @Test
    fun `estimated and realized come-cotas do not double count after checkpoint materialization`() {
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal("95"),
                averageCost = BigDecimal("1.00"),
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = "12345678000199",
                assetType = "MUTUAL_FUND",
                taxProfile = FundTaxProfile.COME_COTAS_LONG,
                latestNav =
                    FundMarketNav(
                        date = LocalDate.of(2025, 12, 1),
                        closePrice = BigDecimal("1.20"),
                    ),
            )
        val transactions =
            listOf(
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holding.id!!,
                    financialAccountId = holding.financialAccountId,
                    accountId = holding.accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("1.00"),
                    grossValue = BigDecimal("100.00"),
                    tradeDate = LocalDate.of(2025, 1, 2),
                    createdAt = Instant.parse("2025-01-02T12:00:00Z"),
                    updatedAt = Instant.parse("2025-01-02T12:00:00Z"),
                ),
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holding.id,
                    financialAccountId = holding.financialAccountId,
                    accountId = holding.accountId,
                    createdByUserId = UUID.randomUUID(),
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.WITHHOLDING_TAX,
                    quantity = BigDecimal("5"),
                    unitPrice = BigDecimal("0.60"),
                    grossValue = BigDecimal("3.00"),
                    tradeDate = LocalDate.of(2025, 5, 30),
                    createdAt = Instant.parse("2025-05-30T12:00:00Z"),
                    updatedAt = Instant.parse("2025-05-30T12:00:00Z"),
                ),
            )

        val result = service.estimate(holding, snapshot, transactions)

        assertThat(result.grossValue).isEqualByComparingTo("114.00")
        assertThat(result.iof).isEqualByComparingTo("0")
        assertThat(result.incomeTax).isEqualByComparingTo("0.80")
        assertThat(result.netValue).isEqualByComparingTo("113.20")
    }
}
