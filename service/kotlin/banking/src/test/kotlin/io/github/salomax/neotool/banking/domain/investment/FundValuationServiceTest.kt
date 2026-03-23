package io.github.salomax.neotool.banking.domain.investment

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
}
