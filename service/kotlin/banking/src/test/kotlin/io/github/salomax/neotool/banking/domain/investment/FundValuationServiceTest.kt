package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointRepository
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
    private val checkpointRepository = FakeComeCotasCheckpointRepository()
    private val service = FundValuationService(checkpointRepository)

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
    fun `COME_COTAS_SHORT keeps the 180-day final-rate boundary`() {
        val tradeDate = LocalDate.of(2026, 1, 1)
        val result =
            service.estimate(
                holding = holding(quantity = "100"),
                snapshot =
                    snapshot(
                        assetType = "MUTUAL_FUND",
                        taxProfile = FundTaxProfile.COME_COTAS_SHORT,
                        navDate = tradeDate.plusDays(180),
                        navPrice = "1.20",
                    ),
                transactions =
                    listOf(
                        purchase(
                            holdingId = holdingId,
                            tradeDate = tradeDate,
                            quantity = "100",
                            unitPrice = "1.00",
                        ),
                    ),
            )

        assertThat(result.incomeTax).isEqualByComparingTo("4.500")
        assertThat(result.iof).isEqualByComparingTo("0")
    }

    @Test
    fun `COME_COTAS_LONG keeps the registry-driven regressive final rate`() {
        val tradeDate = LocalDate.of(2026, 1, 1)
        val result =
            service.estimate(
                holding = holding(quantity = "100"),
                snapshot =
                    snapshot(
                        assetType = "MUTUAL_FUND",
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        navDate = tradeDate.plusDays(181),
                        navPrice = "1.20",
                    ),
                transactions =
                    listOf(
                        purchase(
                            holdingId = holdingId,
                            tradeDate = tradeDate,
                            quantity = "100",
                            unitPrice = "1.00",
                        ),
                    ),
            )

        assertThat(result.incomeTax).isEqualByComparingTo("4.000")
        assertThat(result.iof).isEqualByComparingTo("0")
    }

    @Test
    fun `VARIABLE_INCOME_15 keeps the flat 15 percent final rate`() {
        val tradeDate = LocalDate.of(2026, 1, 1)
        val result =
            service.estimate(
                holding = holding(quantity = "100"),
                snapshot =
                    snapshot(
                        assetType = "EQUITY_FUND",
                        taxProfile = FundTaxProfile.VARIABLE_INCOME_15,
                        navDate = tradeDate.plusDays(181),
                        navPrice = "1.20",
                    ),
                transactions =
                    listOf(
                        purchase(
                            holdingId = holdingId,
                            tradeDate = tradeDate,
                            quantity = "100",
                            unitPrice = "1.00",
                        ),
                    ),
            )

        assertThat(result.incomeTax).isEqualByComparingTo("3.000")
        assertThat(result.iof).isEqualByComparingTo("0")
    }

    @Test
    fun `pgbl asset type with non pension tax profile does not crash valuation`() {
        val tradeDate = LocalDate.of(2026, 1, 1)
        val result =
            service.estimate(
                holding = holding(quantity = "100"),
                snapshot =
                    snapshot(
                        assetType = "PGBL",
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        navDate = tradeDate.plusDays(181),
                        navPrice = "1.20",
                    ),
                transactions =
                    listOf(
                        purchase(
                            holdingId = holdingId,
                            tradeDate = tradeDate,
                            quantity = "100",
                            unitPrice = "1.00",
                        ),
                    ),
            )

        assertThat(result.incomeTax).isEqualByComparingTo("4.000")
        assertThat(result.iof).isEqualByComparingTo("0")
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
    fun `existing checkpoint rows keep live valuation aligned with checkpoint service`() {
        val holding =
            holding(
                quantity = "95",
                averageCost = "1.00",
            )
        checkpointRepository.seed(
            ComeCotasCheckpoint(
                holdingId = holdingId,
                checkpointDate = LocalDate.of(2026, 5, 29),
                computedAmount = BigDecimal("3.00"),
                withheldAmount = BigDecimal("1.50"),
            ),
        )
        val result =
            service.estimate(
                holding = holding,
                snapshot =
                    snapshot(
                        assetType = "MUTUAL_FUND",
                        taxProfile = FundTaxProfile.COME_COTAS_LONG,
                        navDate = LocalDate.of(2026, 11, 30),
                        navPrice = "1.20",
                    ),
                transactions =
                    listOf(
                        purchase(
                            holdingId = holdingId,
                            tradeDate = LocalDate.of(2026, 1, 2),
                            quantity = "100",
                            unitPrice = "1.00",
                        ),
                        withholdingTax(
                            holdingId = holdingId,
                            tradeDate = LocalDate.of(2026, 5, 29),
                            quantity = "5.00000000",
                            amount = "0.70",
                        ),
                    ),
            )

        assertThat(result.grossValue).isEqualByComparingTo("114.00")
        assertThat(result.iof).isEqualByComparingTo("0")
        assertThat(result.incomeTax).isEqualByComparingTo("2.30")
        assertThat(result.netValue).isEqualByComparingTo("111.70")
    }

    private val holdingId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()

    private fun holding(
        quantity: String,
        averageCost: String = "1.00",
    ) = Holding(
        id = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        assetId = assetId,
        quantity = BigDecimal(quantity),
        averageCost = BigDecimal(averageCost),
    )

    private fun snapshot(
        assetType: String,
        taxProfile: FundTaxProfile,
        navDate: LocalDate,
        navPrice: String,
    ) = FundMarketSnapshot(
        fundCnpj = "12345678000199",
        assetType = assetType,
        taxProfile = taxProfile,
        latestNav =
            FundMarketNav(
                date = navDate,
                closePrice = BigDecimal(navPrice),
            ),
    )

    private fun purchase(
        holdingId: UUID,
        tradeDate: LocalDate,
        quantity: String,
        unitPrice: String,
    ) = InvestmentTransaction(
        id = UUID.randomUUID(),
        holdingId = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = UUID.randomUUID(),
        direction = InvestmentTransactionDirection.INFLOW,
        type = InvestmentTransactionType.PURCHASE,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(unitPrice),
        grossValue = BigDecimal(quantity).multiply(BigDecimal(unitPrice)),
        tradeDate = tradeDate,
        createdAt = tradeDate.atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC),
        updatedAt = tradeDate.atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC),
    )

    private fun withholdingTax(
        holdingId: UUID,
        tradeDate: LocalDate,
        amount: String,
        quantity: String = "0.00000000",
    ) = InvestmentTransaction(
        id = UUID.randomUUID(),
        holdingId = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = UUID.randomUUID(),
        direction = InvestmentTransactionDirection.OUTFLOW,
        type = InvestmentTransactionType.WITHHOLDING_TAX,
        quantity = BigDecimal(quantity),
        grossValue = BigDecimal(amount),
        tradeDate = tradeDate,
        createdAt = tradeDate.atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC),
        updatedAt = tradeDate.atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC),
    )

    private class FakeComeCotasCheckpointRepository : ComeCotasCheckpointRepository {
        private val checkpoints = mutableListOf<ComeCotasCheckpoint>()

        fun seed(vararg rows: ComeCotasCheckpoint) {
            checkpoints.clear()
            checkpoints.addAll(rows)
        }

        override fun upsert(checkpoint: ComeCotasCheckpoint): ComeCotasCheckpoint {
            checkpoints.removeIf {
                it.holdingId == checkpoint.holdingId && it.checkpointDate == checkpoint.checkpointDate
            }
            checkpoints.add(checkpoint)
            return checkpoint
        }

        override fun findByHoldingAndDateRange(
            holdingId: UUID,
            from: LocalDate,
            to: LocalDate,
        ): List<ComeCotasCheckpoint> =
            checkpoints.filter {
                it.holdingId == holdingId &&
                    !it.checkpointDate.isBefore(from) &&
                    !it.checkpointDate.isAfter(to)
            }
    }
}
