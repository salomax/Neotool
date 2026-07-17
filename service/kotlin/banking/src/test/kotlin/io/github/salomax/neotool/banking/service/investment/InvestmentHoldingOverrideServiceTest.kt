package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class InvestmentHoldingOverrideServiceTest {
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()
    private val service = InvestmentHoldingOverrideService(holdingRepository, assetRepository)

    @Test
    fun `persists come-cotas override on mutual fund holding`() {
        val now = Instant.parse("2026-07-15T12:00:00Z")
        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                assetId = UUID.randomUUID(),
                quantity = BigDecimal.ONE,
                averageCost = BigDecimal.ONE,
                createdAt = now,
                updatedAt = now,
            )
        whenever(holdingRepository.findById(holding.id!!, holding.accountId)).thenReturn(holding)
        whenever(assetRepository.findById(holding.assetId)).thenReturn(
            Asset(
                id = holding.assetId,
                name = "Fund",
                assetType = AssetType.MUTUAL_FUND,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            ),
        )
        whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

        val updated = service.setComeCotasEnabled(holding.id, holding.accountId, true)

        assertThat(updated.comeCotasEnabled).isTrue()
    }
}
