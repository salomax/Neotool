package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.FundDailyNav
import io.github.salomax.neotool.financialdata.domain.InvestmentFund
import io.github.salomax.neotool.financialdata.repo.InvestmentFundRepositoryCustom
import io.github.salomax.neotool.financialdata.service.InvestmentFundSearchService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class InvestmentFundSearchServiceTest {
    private val fundRepository: InvestmentFundRepositoryCustom = mock()
    private lateinit var service: InvestmentFundSearchService

    @BeforeEach
    fun setUp() {
        service = InvestmentFundSearchService(fundRepository)
    }

    @Test
    fun `searchFundsAutocomplete throws when query is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.searchFundsAutocomplete("  ")
        }
    }

    @Test
    fun `searchFundsAutocomplete trims query and delegates to repository`() {
        whenever(fundRepository.searchFundsAutocomplete(any(), any())).thenReturn(emptyList())

        service.searchFundsAutocomplete(" fii ")

        verify(fundRepository).searchFundsAutocomplete(eq("fii"), eq(10))
    }

    @Test
    fun `searchFundsAutocomplete returns results from repository`() {
        whenever(fundRepository.searchFundsAutocomplete(eq("fii"), eq(10))).thenReturn(
            listOf(
                investmentFund("Fund A"),
                investmentFund("Fund B"),
            ),
        )

        val result = service.searchFundsAutocomplete("fii")

        assertThat(result).hasSize(2)
    }

    @Test
    fun `searchFunds validates page size`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.searchFunds(
                query = null,
                assetType = null,
                isActive = null,
                first = 0,
                after = null,
            )
        }
    }

    @Test
    fun `searchFunds throws for invalid cursor`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.searchFunds(
                    query = null,
                    assetType = null,
                    isActive = null,
                    first = 10,
                    after = "!!!invalid!!!",
                )
            }

        assertThat(ex.message).contains("cursor")
    }

    @Test
    fun `searchFunds returns connection with correct edges`() {
        whenever(
            fundRepository.searchFunds(eq("fii"), eq("FII"), eq(true), eq(6), isNull()),
        ).thenReturn(Pair(listOf(investmentFund("Fund A")), 1L))

        val result =
            service.searchFunds(
                query = "fii",
                assetType = "FII",
                isActive = true,
                first = 5,
                after = null,
            )

        assertThat(result.edges).hasSize(1)
        assertThat(result.totalCount).isEqualTo(1L)
    }

    @Test
    fun `searchFunds sets hasMore when extra item returned`() {
        whenever(
            fundRepository.searchFunds(eq(null), eq(null), eq(null), eq(3), isNull()),
        ).thenReturn(
            Pair(
                listOf(
                    investmentFund("Fund A"),
                    investmentFund("Fund B"),
                    investmentFund("Fund C"),
                ),
                3L,
            ),
        )

        val result = service.searchFunds(null, null, null, 2, null)

        assertThat(result.pageInfo.hasNextPage).isTrue()
        assertThat(result.edges).hasSize(2)
    }

    @Test
    fun `searchFunds normalizes blank query to null`() {
        whenever(
            fundRepository.searchFunds(eq(null), eq(null), eq(null), eq(21), isNull()),
        ).thenReturn(Pair(emptyList(), 0L))

        service.searchFunds("  ", null, null, null, null)

        verify(fundRepository).searchFunds(eq(null), eq(null), eq(null), eq(21), isNull())
    }

    @Test
    fun `findFundById delegates to repository`() {
        val id = UUID.randomUUID()
        val expected = investmentFund("Fund A", id = id)
        whenever(fundRepository.findFundById(id)).thenReturn(expected)

        val result = service.findFundById(id)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `findFundById returns null when not found`() {
        val id = UUID.randomUUID()
        whenever(fundRepository.findFundById(id)).thenReturn(null)

        val result = service.findFundById(id)

        assertThat(result).isNull()
    }

    @Test
    fun `getPriceHistory delegates to repository`() {
        val assetId = UUID.randomUUID()
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        whenever(fundRepository.getPriceHistory(any(), any(), any(), any())).thenReturn(emptyList())

        service.getPriceHistory(assetId, from, to, 30)

        verify(fundRepository).getPriceHistory(eq(assetId), eq(from), eq(to), eq(30))
    }

    @Test
    fun `getMarketSnapshotsByCnpj delegates to repository`() {
        val cnpjs = listOf("11222333000181")
        whenever(fundRepository.findMarketSnapshotsByCnpj(any())).thenReturn(emptyList())

        service.getMarketSnapshotsByCnpj(cnpjs)

        verify(fundRepository).findMarketSnapshotsByCnpj(eq(cnpjs))
    }

    @Test
    fun `getNavSeriesByFundCnpj delegates to repository`() {
        val cnpj = "11222333000181"
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        whenever(fundRepository.getNavSeriesByFundCnpj(any(), any(), any())).thenReturn(emptyList())

        service.getNavSeriesByFundCnpj(cnpj, from, to)

        verify(fundRepository).getNavSeriesByFundCnpj(eq(cnpj), eq(from), eq(to))
    }

    private fun investmentFund(
        name: String,
        id: UUID = UUID.randomUUID(),
    ): InvestmentFund =
        InvestmentFund(
            id = id,
            name = name,
            cnpj = "11222333000181",
            assetType = "FII",
            isActive = true,
            latestNav =
                FundDailyNav(
                    date = LocalDate.of(2024, 1, 1),
                    closePrice = BigDecimal("1.23"),
                    totalNetAssets = BigDecimal("1000000"),
                ),
            simScore = 0.9,
        )
}
