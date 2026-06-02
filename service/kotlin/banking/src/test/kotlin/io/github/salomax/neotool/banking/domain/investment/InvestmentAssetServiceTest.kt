package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.service.investment.InvestmentAssetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class InvestmentAssetServiceTest {
    private lateinit var assetRepository: AssetRepository
    private lateinit var holdingRepository: HoldingRepository
    private lateinit var service: InvestmentAssetService

    @BeforeEach
    fun setUp() {
        assetRepository = mock()
        holdingRepository = mock()
        service = InvestmentAssetService(assetRepository, holdingRepository)
    }

    @Test
    fun `reuses existing asset by normalized ticker`() {
        val existing =
            Asset(
                id = UUID.randomUUID(),
                ticker = "PETR4",
                name = "Petrobras PN",
                assetType = AssetType.STOCK,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        whenever(assetRepository.findByTicker("PETR4")).thenReturn(existing)

        val result =
            service.create(
                name = "Petrobras PN",
                assetType = AssetType.STOCK,
                issuerName = null,
                fundCnpj = null,
                ticker = " petr4 ",
                isinCode = null,
                currencyCode = "BRL",
            )

        assertThat(result).isEqualTo(existing)
        verify(assetRepository).findByTicker("PETR4")
        verify(assetRepository, never()).save(any())
    }

    @Test
    fun `saves new asset with normalized ticker`() {
        whenever(assetRepository.findByTicker("VALE3")).thenReturn(null)
        whenever(assetRepository.save(any())).thenAnswer { it.arguments[0] as Asset }

        val result =
            service.create(
                name = "Vale ON",
                assetType = AssetType.STOCK,
                issuerName = null,
                fundCnpj = null,
                ticker = " vale3 ",
                isinCode = null,
                currencyCode = "BRL",
            )

        assertThat(result.ticker).isEqualTo("VALE3")
        verify(assetRepository).save(any())
    }

    @Test
    fun `update fails when account has no holding for asset`() {
        val assetId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        whenever(holdingRepository.findByAccount(accountId)).thenReturn(emptyList())

        assertThrows<IllegalArgumentException> {
            service.update(
                id = assetId,
                accountId = accountId,
                name = "Novo nome",
                assetType = AssetType.CDB,
                issuerName = "Banco X",
                fundCnpj = null,
                ticker = null,
                isinCode = null,
                currencyCode = "BRL",
            )
        }

        verify(assetRepository, never()).findById(any())
        verify(assetRepository, never()).update(any())
    }
}
