package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingCloseReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class HoldingMapperTest {
    @Test
    fun `round trip preserves closedAt and closeReason`() {
        val fa = UUID.randomUUID()
        val acc = UUID.randomUUID()
        val asset = UUID.randomUUID()
        val closed = LocalDate.of(2026, 3, 1)
        val domain =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = fa,
                accountId = acc,
                assetId = asset,
                quantity = BigDecimal.ZERO,
                closedAt = closed,
                closeReason = HoldingCloseReason.MATURED,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            )
        val entity = HoldingMapper.toEntity(domain)
        assertThat(entity.closedAt).isEqualTo(closed)
        assertThat(entity.closeReason).isEqualTo(HoldingCloseReason.MATURED)

        val back = HoldingMapper.toDomain(entity)
        assertThat(back.closedAt).isEqualTo(closed)
        assertThat(back.closeReason).isEqualTo(HoldingCloseReason.MATURED)
    }

    @Test
    fun `null close fields round trip`() {
        val domain =
            Holding(
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal.ONE,
                comeCotasEnabled = true,
                administrationFeeAnnualPercent = BigDecimal("1.50"),
            )
        val round = HoldingMapper.toDomain(HoldingMapper.toEntity(domain))
        assertThat(round.closedAt).isNull()
        assertThat(round.closeReason).isNull()
        assertThat(round.comeCotasEnabled).isTrue()
        assertThat(round.administrationFeeAnnualPercent).isEqualByComparingTo("1.50")
    }
}
