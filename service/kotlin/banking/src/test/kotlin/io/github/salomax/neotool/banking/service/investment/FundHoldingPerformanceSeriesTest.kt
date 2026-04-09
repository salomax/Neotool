package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.FundMarketNav
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class FundHoldingPerformanceSeriesTest {
    private val fundValuationService = FundValuationService()
    private val cnpj = "12345678000199"
    private val snapshotUnknownTax =
        FundMarketSnapshot(
            fundCnpj = cnpj,
            assetType = "MUTUAL_FUND",
            taxProfile = FundTaxProfile.UNKNOWN,
            latestNav = FundMarketNav(LocalDate.of(2025, 1, 31), BigDecimal.TEN),
        )

    @Test
    fun `returns empty when no transactions`() {
        val holding = baseHolding()
        val nav = listOf(LocalDate.of(2025, 1, 31) to BigDecimal.TEN)
        val result =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = emptyList(),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 3, 1),
            )
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty when nav series empty`() {
        val holding = baseHolding()
        val tx = purchase(holding, LocalDate.of(2025, 1, 10), qty = BigDecimal("100"), price = BigDecimal.ONE)
        val result =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(tx),
                navSeries = emptyList(),
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 3, 1),
            )
        assertThat(result).isEmpty()
    }

    @Test
    fun `single purchase without redemptions produces non-empty series`() {
        val holding = baseHolding()
        val trade = LocalDate.of(2025, 1, 10)
        val tx = purchase(holding, trade, qty = BigDecimal("100"), price = BigDecimal("2.00"))
        val nav =
            listOf(
                trade to BigDecimal("1.50"),
                LocalDate.of(2025, 1, 31) to BigDecimal("2.00"),
                LocalDate.of(2025, 2, 28) to BigDecimal("2.50"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(tx),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 2, 28),
            )
        assertThat(points).isNotEmpty
        assertThat(points.all { !it.isProjected }).isTrue()
        val last = points.last()
        assertThat(last.quantity).isEqualByComparingTo(BigDecimal("100"))
        assertThat(last.grossValue).isEqualByComparingTo(BigDecimal("250.00"))
    }

    @Test
    fun `partial sale reduces quantity and gross at month end`() {
        val holding = baseHolding()
        val buy = purchase(holding, LocalDate.of(2025, 1, 5), qty = BigDecimal("100"), price = BigDecimal.ONE)
        val sell =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holding.id!!,
                financialAccountId = holding.financialAccountId,
                accountId = holding.accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.OUTFLOW,
                type = InvestmentTransactionType.SALE,
                quantity = BigDecimal("40"),
                unitPrice = BigDecimal("1.20"),
                grossValue = BigDecimal("48.00"),
                tradeDate = LocalDate.of(2025, 2, 15),
                source = InvestmentSource.MANUAL,
                createdAt = Instant.parse("2025-02-15T12:00:00Z"),
                updatedAt = Instant.parse("2025-02-15T12:00:00Z"),
            )
        val nav =
            listOf(
                LocalDate.of(2025, 1, 1) to BigDecimal.ONE,
                LocalDate.of(2025, 2, 28) to BigDecimal("2.00"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(buy, sell),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 2, 28),
            )
        val last = points.last()
        assertThat(last.quantity).isEqualByComparingTo(BigDecimal("60"))
        assertThat(last.grossValue).isEqualByComparingTo(BigDecimal("120.00"))
    }

    @Test
    fun `uses latest NAV on or before point date when gap in series`() {
        val holding = baseHolding()
        val tx = purchase(holding, LocalDate.of(2025, 1, 5), qty = BigDecimal("10"), price = BigDecimal.ONE)
        val nav =
            listOf(
                LocalDate.of(2025, 1, 2) to BigDecimal("5.00"),
                LocalDate.of(2025, 3, 1) to BigDecimal("6.00"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(tx),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 2, 28),
            )
        assertThat(points).hasSize(2)
        assertThat(points[1].grossValue).isEqualByComparingTo(BigDecimal("50.00"))
    }

    @Test
    fun `returns final month point when first NAV arrives after purchase date but before month end`() {
        val holding = baseHolding()
        val tx = purchase(holding, LocalDate.of(2025, 2, 10), qty = BigDecimal.TEN, price = BigDecimal.ONE)
        val nav = listOf(LocalDate.of(2025, 3, 1) to BigDecimal("2.00"))
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(tx),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 3, 31),
            )
        assertThat(points).hasSize(1)
        assertThat(LocalDate.ofInstant(points.single().timestamp, ZoneOffset.UTC)).isEqualTo(LocalDate.of(2025, 3, 31))
        assertThat(points.single().grossValue).isEqualByComparingTo(BigDecimal("20.00"))
    }

    @Test
    fun `multiple purchases on same trade date ordered by createdAt update average cost`() {
        val holding = baseHolding()
        val day = LocalDate.of(2025, 1, 15)
        val first =
            purchase(
                holding,
                day,
                qty = BigDecimal("100"),
                price = BigDecimal.ONE,
                createdAt = Instant.parse("2025-01-15T10:00:00Z"),
            )
        val second =
            purchase(
                holding,
                day,
                qty = BigDecimal("100"),
                price = BigDecimal("3.00"),
                createdAt = Instant.parse("2025-01-15T14:00:00Z"),
            )
        val nav =
            listOf(
                LocalDate.of(2025, 1, 1) to BigDecimal("2.00"),
                LocalDate.of(2025, 1, 31) to BigDecimal("2.00"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(first, second),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 1, 31),
            )
        val last = points.last()
        assertThat(last.quantity).isEqualByComparingTo(BigDecimal("200"))
        assertThat(last.currentCostBasis).isEqualByComparingTo(BigDecimal("400.00"))
        assertThat(last.grossValue).isEqualByComparingTo(BigDecimal("400.00"))
    }

    @Test
    fun `custody transfer inflow combines with purchase for average cost`() {
        val holding = baseHolding()
        val day = LocalDate.of(2025, 2, 1)
        val transfer =
            transferIn(
                holding,
                day,
                InvestmentTransactionType.CUSTODY_TRANSFER,
                qty = BigDecimal("50"),
                price = BigDecimal("2.00"),
                createdAt = Instant.parse("2025-02-01T09:00:00Z"),
            )
        val buy =
            purchase(
                holding,
                day,
                qty = BigDecimal("50"),
                price = BigDecimal.ONE,
                createdAt = Instant.parse("2025-02-01T10:00:00Z"),
            )
        val nav =
            listOf(
                LocalDate.of(2025, 2, 1) to BigDecimal("1.50"),
                LocalDate.of(2025, 2, 28) to BigDecimal("1.50"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(transfer, buy),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 2, 28),
            )
        val last = points.last()
        assertThat(last.quantity).isEqualByComparingTo(BigDecimal("100"))
        assertThat(last.currentCostBasis).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(last.grossValue).isEqualByComparingTo(BigDecimal("150.00"))
    }

    @Test
    fun `quota transfer outflow reduces quantity like sale`() {
        val holding = baseHolding()
        val buy = purchase(holding, LocalDate.of(2025, 1, 5), qty = BigDecimal("100"), price = BigDecimal.ONE)
        val transferOut =
            transferOut(
                holding,
                LocalDate.of(2025, 2, 10),
                InvestmentTransactionType.QUOTA_TRANSFER,
                qty = BigDecimal("30"),
                grossValue = BigDecimal("30.00"),
                createdAt = Instant.parse("2025-02-10T12:00:00Z"),
            )
        val nav =
            listOf(
                LocalDate.of(2025, 1, 1) to BigDecimal.ONE,
                LocalDate.of(2025, 2, 28) to BigDecimal("2.00"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(buy, transferOut),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 2, 28),
            )
        assertThat(points.last().quantity).isEqualByComparingTo(BigDecimal("70"))
        assertThat(points.last().grossValue).isEqualByComparingTo(BigDecimal("140.00"))
    }

    @Test
    fun `full redemption in same month as purchase ends with zero position point`() {
        val holding = baseHolding()
        val buy =
            purchase(
                holding,
                LocalDate.of(2025, 1, 5),
                qty = BigDecimal("100"),
                price = BigDecimal.ONE,
                createdAt = Instant.parse("2025-01-05T10:00:00Z"),
            )
        val sell =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holding.id!!,
                financialAccountId = holding.financialAccountId,
                accountId = holding.accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.OUTFLOW,
                type = InvestmentTransactionType.SALE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("1.10"),
                grossValue = BigDecimal("110.00"),
                tradeDate = LocalDate.of(2025, 1, 20),
                source = InvestmentSource.MANUAL,
                createdAt = Instant.parse("2025-01-20T12:00:00Z"),
                updatedAt = Instant.parse("2025-01-20T12:00:00Z"),
            )
        val nav =
            listOf(
                LocalDate.of(2025, 1, 1) to BigDecimal.ONE,
                LocalDate.of(2025, 1, 31) to BigDecimal("1.05"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(buy, sell),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 1, 31),
            )
        assertThat(points).hasSize(1)
        assertThat(points.single().quantity).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(points.single().grossValue).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `long gaps without new NAV rows reuse last known NAV for many months`() {
        val holding = baseHolding()
        val tx = purchase(holding, LocalDate.of(2025, 1, 10), qty = BigDecimal("10"), price = BigDecimal.ONE)
        val nav =
            listOf(
                LocalDate.of(2025, 1, 1) to BigDecimal("10.00"),
                LocalDate.of(2025, 10, 31) to BigDecimal("20.00"),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(tx),
                navSeries = nav,
                taxSnapshot = snapshotUnknownTax,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 10, 31),
            )
        assertThat(points).isNotEmpty
        val junePoint =
            points.find { p ->
                LocalDate.ofInstant(p.timestamp, ZoneOffset.UTC) == LocalDate.of(2025, 6, 30)
            }
        assertThat(junePoint).isNotNull
        assertThat(junePoint!!.grossValue).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(points.last().grossValue).isEqualByComparingTo(BigDecimal("200.00"))
    }

    @Test
    fun `pension tax profile yields gross but null net`() {
        val holding = baseHolding()
        val tx =
            purchase(
                holding,
                LocalDate.of(2025, 1, 5),
                qty = BigDecimal("100"),
                price = BigDecimal.TEN,
                createdAt = Instant.parse("2025-01-05T12:00:00Z"),
            )
        val nav = listOf(LocalDate.of(2025, 1, 31) to BigDecimal("12.00"))
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = cnpj,
                assetType = "PGBL",
                taxProfile = FundTaxProfile.PENSION,
                latestNav = FundMarketNav(LocalDate.of(2025, 1, 31), BigDecimal("12.00")),
            )
        val points =
            buildFundNavPerformancePoints(
                holding = holding,
                transactions = listOf(tx),
                navSeries = nav,
                taxSnapshot = snapshot,
                fundValuationService = fundValuationService,
                today = LocalDate.of(2025, 1, 31),
            )
        val last = points.last()
        assertThat(last.grossValue).isEqualByComparingTo(BigDecimal("1200.00"))
        assertThat(last.netValue).isNull()
    }

    @Test
    fun `come cotas long and short produce different net for same gain`() {
        val holding = baseHolding()
        val tradeDate = LocalDate.of(2025, 1, 2)
        val tx =
            purchase(
                holding,
                tradeDate,
                qty = BigDecimal("100"),
                price = BigDecimal("10.00"),
                createdAt = Instant.parse("2025-01-02T12:00:00Z"),
            )
        val navDate = LocalDate.of(2025, 12, 31)
        val nav =
            listOf(
                LocalDate.of(2025, 1, 1) to BigDecimal("10.00"),
                navDate to BigDecimal("15.00"),
            )

        fun lastNet(profile: FundTaxProfile): BigDecimal? {
            val snap =
                FundMarketSnapshot(
                    fundCnpj = cnpj,
                    assetType = "MUTUAL_FUND",
                    taxProfile = profile,
                    latestNav = FundMarketNav(navDate, BigDecimal("15.00")),
                )
            val pts =
                buildFundNavPerformancePoints(
                    holding = holding,
                    transactions = listOf(tx),
                    navSeries = nav,
                    taxSnapshot = snap,
                    fundValuationService = fundValuationService,
                    today = navDate,
                )
            assertThat(pts).describedAs("profile=$profile").isNotEmpty
            return pts.last().netValue
        }

        val netLong = lastNet(FundTaxProfile.COME_COTAS_LONG)
        val netShort = lastNet(FundTaxProfile.COME_COTAS_SHORT)
        assertThat(netLong).isNotNull
        assertThat(netShort).isNotNull
        assertThat(netLong).isNotEqualByComparingTo(netShort)
    }

    @Test
    fun `variable income 15 tax profile differs from come cotas long on same gain`() {
        val holding = baseHolding()
        val tradeDate = LocalDate.of(2025, 1, 2)
        val tx =
            purchase(
                holding,
                tradeDate,
                qty = BigDecimal("100"),
                price = BigDecimal("10.00"),
                createdAt = Instant.parse("2025-01-02T12:00:00Z"),
            )
        val navDate = LocalDate.of(2025, 12, 31)
        val nav =
            listOf(
                LocalDate.of(2025, 1, 1) to BigDecimal("10.00"),
                navDate to BigDecimal("15.00"),
            )

        fun lastNet(profile: FundTaxProfile): BigDecimal {
            val snap =
                FundMarketSnapshot(
                    fundCnpj = cnpj,
                    assetType = "ETF",
                    taxProfile = profile,
                    latestNav = FundMarketNav(navDate, BigDecimal("15.00")),
                )
            val pts =
                buildFundNavPerformancePoints(
                    holding = holding,
                    transactions = listOf(tx),
                    navSeries = nav,
                    taxSnapshot = snap,
                    fundValuationService = fundValuationService,
                    today = navDate,
                )
            assertThat(pts).isNotEmpty
            return requireNotNull(pts.last().netValue)
        }

        assertThat(lastNet(FundTaxProfile.VARIABLE_INCOME_15))
            .isNotEqualByComparingTo(lastNet(FundTaxProfile.COME_COTAS_LONG))
    }

    private fun baseHolding(): Holding {
        val now = Instant.parse("2025-01-01T12:00:00Z")
        return Holding(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            assetId = UUID.randomUUID(),
            quantity = BigDecimal.ZERO,
            averageCost = BigDecimal.ZERO,
            pricingMode = HoldingPricingMode.MARKET_DATA,
            source = InvestmentSource.MANUAL,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun purchase(
        holding: Holding,
        tradeDate: LocalDate,
        qty: BigDecimal,
        price: BigDecimal,
        createdAt: Instant = Instant.parse("2025-01-01T12:00:00Z"),
    ): InvestmentTransaction =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holding.id!!,
            financialAccountId = holding.financialAccountId,
            accountId = holding.accountId,
            createdByUserId = UUID.randomUUID(),
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = qty,
            unitPrice = price,
            grossValue = qty.multiply(price),
            tradeDate = tradeDate,
            source = InvestmentSource.MANUAL,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun transferIn(
        holding: Holding,
        tradeDate: LocalDate,
        type: InvestmentTransactionType,
        qty: BigDecimal,
        price: BigDecimal,
        createdAt: Instant,
    ): InvestmentTransaction =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holding.id!!,
            financialAccountId = holding.financialAccountId,
            accountId = holding.accountId,
            createdByUserId = UUID.randomUUID(),
            direction = InvestmentTransactionDirection.INFLOW,
            type = type,
            quantity = qty,
            unitPrice = price,
            grossValue = qty.multiply(price),
            tradeDate = tradeDate,
            source = InvestmentSource.MANUAL,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun transferOut(
        holding: Holding,
        tradeDate: LocalDate,
        type: InvestmentTransactionType,
        qty: BigDecimal,
        grossValue: BigDecimal,
        createdAt: Instant,
    ): InvestmentTransaction =
        InvestmentTransaction(
            id = UUID.randomUUID(),
            holdingId = holding.id!!,
            financialAccountId = holding.financialAccountId,
            accountId = holding.accountId,
            createdByUserId = UUID.randomUUID(),
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = type,
            quantity = qty,
            unitPrice = null,
            grossValue = grossValue,
            tradeDate = tradeDate,
            source = InvestmentSource.MANUAL,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
}
