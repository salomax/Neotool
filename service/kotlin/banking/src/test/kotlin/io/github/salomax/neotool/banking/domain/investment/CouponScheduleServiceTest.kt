package io.github.salomax.neotool.banking.domain.investment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class CouponScheduleServiceTest {
    private val service = CouponScheduleService()

    private fun holding(
        issueDate: LocalDate,
        maturityDate: LocalDate,
        couponFrequency: CouponFrequency,
        couponDayOfMonth: Int? = null,
        indexer: Indexer = Indexer.CDI,
    ): Holding =
        Holding(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            assetId = UUID.randomUUID(),
            quantity = BigDecimal("10000"),
            averageCost = BigDecimal.ONE,
            issueDate = issueDate,
            maturityDate = maturityDate,
            couponFrequency = couponFrequency,
            couponDayOfMonth = couponDayOfMonth,
            indexer = indexer,
            indexerPercentage = BigDecimal.ONE,
        )

    @Test
    fun `legacy Tesouro schedule from maturity stepping back by frequency`() {
        val h =
            holding(
                issueDate = LocalDate.of(2025, 1, 10),
                maturityDate = LocalDate.of(2025, 7, 10),
                couponFrequency = CouponFrequency.QUARTERLY,
            )

        val schedule =
            service.generateSchedule(
                h,
                AssetType.TREASURY,
                emptyList(),
                today = LocalDate.of(2025, 2, 1),
            )

        assertThat(schedule).hasSize(2)
        assertThat(schedule[0].seriesNumber).isEqualTo(1)
        assertThat(schedule[0].scheduledDate).isEqualTo(LocalDate.of(2025, 4, 10))
        assertThat(schedule[0].isPartialPeriod).isFalse()
        assertThat(schedule[1].scheduledDate).isEqualTo(LocalDate.of(2025, 7, 10))
    }

    @Test
    fun `matches interest payment within seven days on legacy schedule`() {
        val h =
            holding(
                issueDate = LocalDate.of(2025, 1, 1),
                maturityDate = LocalDate.of(2025, 12, 31),
                couponFrequency = CouponFrequency.SEMI_ANNUAL,
            )
        val payment =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = h.id!!,
                financialAccountId = h.financialAccountId,
                accountId = h.accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.INTEREST_PAYMENT,
                grossValue = BigDecimal("123.45"),
                tradeDate = LocalDate.of(2025, 7, 2),
                source = InvestmentSource.MANUAL,
            )

        val schedule =
            service.generateSchedule(
                h,
                AssetType.TREASURY,
                listOf(payment),
                today = LocalDate.of(2025, 8, 1),
            )
        val juneEntry = schedule.firstOrNull { it.scheduledDate == LocalDate.of(2025, 6, 30) }

        assertThat(juneEntry).isNotNull
        assertThat(juneEntry!!.matchedTransaction?.id).isEqualTo(payment.id)
    }

    @Test
    fun `returns empty schedule when tenor is shorter than coupon period legacy`() {
        val h =
            holding(
                issueDate = LocalDate.of(2025, 1, 1),
                maturityDate = LocalDate.of(2025, 3, 1),
                couponFrequency = CouponFrequency.SEMI_ANNUAL,
            )

        val schedule =
            service.generateSchedule(
                h,
                AssetType.TREASURY,
                emptyList(),
                today = LocalDate.of(2025, 2, 1),
            )

        assertThat(schedule).isEmpty()
    }

    @Test
    fun `BR-P2-1 NTN-B windows by calendar year parity first after issue excludes maturity`() {
        val h =
            holding(
                issueDate = LocalDate.of(2026, 4, 10),
                maturityDate = LocalDate.of(2028, 8, 15),
                couponFrequency = CouponFrequency.SEMI_ANNUAL,
                indexer = Indexer.IPCA,
            )
        val schedule =
            service.generateSchedule(
                h,
                AssetType.TREASURY,
                emptyList(),
                today = LocalDate.of(2026, 5, 1),
            )

        assertThat(schedule.map { it.scheduledDate })
            .containsExactly(
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2027, 5, 15),
                LocalDate.of(2027, 11, 15),
                LocalDate.of(2028, 2, 15),
            )
        assertThat(schedule[0].periodStart).isEqualTo(LocalDate.of(2026, 4, 10))
        assertThat(schedule[0].periodEnd).isEqualTo(LocalDate.of(2026, 8, 15))
    }

    @Test
    fun `BR-P2-2 NTN-F Jan and Jul first strictly after issue`() {
        val h =
            holding(
                issueDate = LocalDate.of(2026, 1, 2),
                maturityDate = LocalDate.of(2027, 7, 1),
                couponFrequency = CouponFrequency.SEMI_ANNUAL,
                indexer = Indexer.PRE_FIXADO,
            )
        val schedule =
            service.generateSchedule(
                h,
                AssetType.TREASURY,
                emptyList(),
                today = LocalDate.of(2026, 2, 1),
            )
        assertThat(schedule.map { it.scheduledDate })
            .containsExactly(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 1, 1),
            )
    }

    @Test
    fun `BR-P2-3 monthly private credit payment month after accrual partial first period`() {
        val h =
            holding(
                issueDate = LocalDate.of(2026, 1, 25),
                maturityDate = LocalDate.of(2026, 5, 20),
                couponFrequency = CouponFrequency.MONTHLY,
                couponDayOfMonth = 15,
            )
        val schedule =
            service.generateSchedule(
                h,
                AssetType.CRA,
                emptyList(),
                today = LocalDate.of(2026, 2, 1),
            )
        assertThat(schedule).hasSizeGreaterThanOrEqualTo(2)
        assertThat(schedule[0].scheduledDate).isEqualTo(LocalDate.of(2026, 2, 15))
        assertThat(schedule[0].periodStart).isEqualTo(LocalDate.of(2026, 1, 25))
        assertThat(schedule[0].periodEnd).isEqualTo(LocalDate.of(2026, 1, 31))
        assertThat(schedule[0].isPartialPeriod).isTrue()
        assertThat(schedule[1].scheduledDate).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(schedule[1].isPartialPeriod).isFalse()
    }

    @Test
    fun `BR-P2-3 semi-annual private credit matches spec example`() {
        val h =
            holding(
                issueDate = LocalDate.of(2026, 1, 10),
                maturityDate = LocalDate.of(2028, 6, 30),
                couponFrequency = CouponFrequency.SEMI_ANNUAL,
                couponDayOfMonth = 15,
            )
        val schedule =
            service.generateSchedule(
                h,
                AssetType.CRI,
                emptyList(),
                today = LocalDate.of(2026, 8, 1),
            )
        assertThat(schedule[0].scheduledDate).isEqualTo(LocalDate.of(2026, 7, 15))
        assertThat(schedule[0].periodEnd).isEqualTo(LocalDate.of(2026, 6, 30))
        assertThat(schedule[0].isPartialPeriod).isTrue()
        assertThat(schedule[1].scheduledDate).isEqualTo(LocalDate.of(2027, 1, 15))
    }

    @Test
    fun `findMatchingEntry uses asset type`() {
        val h =
            holding(
                issueDate = LocalDate.of(2026, 1, 25),
                maturityDate = LocalDate.of(2027, 1, 1),
                couponFrequency = CouponFrequency.MONTHLY,
                couponDayOfMonth = 15,
            )
        val entry =
            service.findMatchingEntry(
                h,
                AssetType.CRA,
                emptyList(),
                tradeDate = LocalDate.of(2026, 2, 14),
            )
        assertThat(entry?.scheduledDate).isEqualTo(LocalDate.of(2026, 2, 15))
    }
}
