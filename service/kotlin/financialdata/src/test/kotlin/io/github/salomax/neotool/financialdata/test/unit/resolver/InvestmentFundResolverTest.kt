package io.github.salomax.neotool.financialdata.test.unit.resolver

import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.financialdata.domain.FundDailyNav
import io.github.salomax.neotool.financialdata.domain.InvestmentFund
import io.github.salomax.neotool.financialdata.graphql.dto.FundDailyNavDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.InvestmentFundMapper
import io.github.salomax.neotool.financialdata.graphql.resolver.InvestmentFundResolver
import io.github.salomax.neotool.financialdata.service.InvestmentFundSearchService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class InvestmentFundResolverTest {
    private val fundSearchService: InvestmentFundSearchService = mock()
    private val mapper: InvestmentFundMapper = mock()
    private lateinit var resolver: InvestmentFundResolver

    @BeforeEach
    fun setUp() {
        resolver = InvestmentFundResolver(fundSearchService, mapper)
    }

    @Test
    fun `searchInvestmentFundsAutocomplete throws when query is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver.searchInvestmentFundsAutocomplete("")
        }
    }

    @Test
    fun `searchInvestmentFundsAutocomplete delegates to service and maps result`() {
        val funds = listOf(investmentFund("Fund A"))
        val expected =
            InvestmentFundAutocompleteConnectionDTO(
                suggestions =
                    listOf(
                        InvestmentFundAutocompleteItemDTO(
                            id = funds[0].id.toString(),
                            name = "Fund A",
                            cnpj = funds[0].cnpj,
                            assetType = funds[0].assetType,
                            isActive = true,
                        ),
                    ),
            )
        whenever(fundSearchService.searchFundsAutocomplete("fii", 10)).thenReturn(funds)
        whenever(mapper.toAutocompleteConnectionDTO(funds)).thenReturn(expected)

        val result = resolver.searchInvestmentFundsAutocomplete("fii")

        assertThat(result).isEqualTo(expected)
        verify(fundSearchService).searchFundsAutocomplete(eq("fii"), eq(10))
        verify(mapper).toAutocompleteConnectionDTO(eq(funds))
    }

    @Test
    fun `investmentFunds validates page size`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver.investmentFunds(null, null, null, 0, null)
        }
    }

    @Test
    fun `investmentFunds delegates to service with correct pageSize`() {
        val connection =
            ConnectionBuilder.buildConnection<InvestmentFund>(
                items = emptyList(),
                hasMore = false,
                encodeCursor = { "" },
            )
        val expected =
            InvestmentFundConnectionDTO(
                edges = emptyList(),
                pageInfo =
                    PageInfoDTO(
                        hasNextPage = false,
                        hasPreviousPage = false,
                        startCursor = null,
                        endCursor = null,
                    ),
                totalCount = 0,
            )
        whenever(
            fundSearchService.searchFunds(eq(null), eq("FII"), eq(true), eq(5), eq("cursor")),
        ).thenReturn(connection)
        whenever(mapper.toConnectionDTO(connection)).thenReturn(expected)

        val result = resolver.investmentFunds(null, "FII", true, 5, "cursor")

        assertThat(result).isEqualTo(expected)
        verify(fundSearchService).searchFunds(eq(null), eq("FII"), eq(true), eq(5), eq("cursor"))
    }

    @Test
    fun `investmentFund throws for invalid UUID`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.investmentFund("not-a-uuid")
            }

        assertThat(ex.message).contains("Invalid fund id")
    }

    @Test
    fun `investmentFund returns null when service returns null`() {
        val id = UUID.randomUUID()
        whenever(fundSearchService.findFundById(id)).thenReturn(null)

        val result = resolver.investmentFund(id.toString())

        assertThat(result).isNull()
    }

    @Test
    fun `investmentFund returns DTO when fund found`() {
        val id = UUID.randomUUID()
        val fund = investmentFund("Fund A", id)
        val expected = fundDto(fund)
        whenever(fundSearchService.findFundById(id)).thenReturn(fund)
        whenever(mapper.toFundDTO(fund)).thenReturn(expected)

        val result = resolver.investmentFund(id.toString())

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `priceHistory delegates with UUID and dates`() {
        val id = UUID.randomUUID()
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        val history = listOf(fundNav())
        val expected = listOf(FundDailyNavDTO(LocalDate.of(2024, 1, 1), 1.2, 1000000.0))
        whenever(fundSearchService.getPriceHistory(id, from, to, 20)).thenReturn(history)
        whenever(mapper.toNavListDTO(history)).thenReturn(expected)

        val result = resolver.priceHistory(id.toString(), from, to, 20)

        assertThat(result).isEqualTo(expected)
        verify(fundSearchService).getPriceHistory(eq(id), eq(from), eq(to), eq(20))
    }

    @Test
    fun `fundNavSeries returns empty list for CNPJ with wrong digit count`() {
        val result =
            resolver.fundNavSeries(
                cnpj = "123",
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 1, 31),
            )

        assertThat(result).isEmpty()
        verify(fundSearchService, never()).getNavSeriesByFundCnpj(any(), any(), any())
    }

    @Test
    fun `fundNavSeries strips non-digit chars and delegates when CNPJ is valid`() {
        val history = listOf(fundNav())
        val expected = listOf(FundDailyNavDTO(LocalDate.of(2024, 1, 1), 1.2, 1000000.0))
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        whenever(fundSearchService.getNavSeriesByFundCnpj("11222333000181", from, to)).thenReturn(history)
        whenever(mapper.toNavListDTO(history)).thenReturn(expected)

        val result = resolver.fundNavSeries("11.222.333/0001-81", from, to)

        assertThat(result).isEqualTo(expected)
        verify(fundSearchService).getNavSeriesByFundCnpj(eq("11222333000181"), eq(from), eq(to))
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
            latestNav = fundNav(),
            simScore = 0.5,
        )

    private fun fundNav(): FundDailyNav =
        FundDailyNav(
            date = LocalDate.of(2024, 1, 1),
            closePrice = BigDecimal("1.2"),
            totalNetAssets = BigDecimal("1000000"),
        )

    private fun fundDto(fund: InvestmentFund): InvestmentFundDTO =
        InvestmentFundDTO(
            id = fund.id.toString(),
            name = fund.name,
            cnpj = fund.cnpj,
            assetType = fund.assetType,
            isActive = fund.isActive,
            patrimonioLiquido = fund.patrimonioLiquido,
            dataPatrimonioLiquido = fund.dataPatrimonioLiquido,
            custodiante = fund.custodiante,
            cnpjCustodiante = fund.cnpjCustodiante,
            auditor = fund.auditor,
            cnpjAuditor = fund.cnpjAuditor,
            publicoAlvo = fund.publicoAlvo,
            taxProfile = "UNKNOWN",
            latestNav = FundDailyNavDTO(LocalDate.of(2024, 1, 1), 1.2, 1000000.0),
        )
}
