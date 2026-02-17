package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepositoryCustom
import io.github.salomax.neotool.financialdata.repo.InstitutionSearchResult
import io.github.salomax.neotool.financialdata.repo.InstitutionSlugResult
import io.github.salomax.neotool.financialdata.service.InstitutionDataRestrictionService
import io.github.salomax.neotool.financialdata.service.InstitutionSearchService
import io.github.salomax.neotool.financialdata.service.QuarterService
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
import java.time.Instant
import java.util.UUID

class InstitutionSearchServiceTest {
    private val institutionSearchRepository: BacenInstitutionRepositoryCustom = mock()
    private val quarterService: QuarterService = mock()
    private val restrictionService: InstitutionDataRestrictionService = mock()
    private lateinit var service: InstitutionSearchService

    @BeforeEach
    fun setUp() {
        whenever(restrictionService.getRestrictionsForInstitutions(any())).thenReturn(emptyMap())
        service = InstitutionSearchService(institutionSearchRepository, quarterService, restrictionService)
    }

    @Test
    fun `searchBacenInstitutionsAutocomplete returns suggestions when query is valid`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.searchBacenInstitutionsAutocomplete(any(), any(), any()),
        ).thenReturn(listOf("Banco do Brasil", "Banco Santander"))

        val result = service.searchBacenInstitutionsAutocomplete("Banco")

        assertThat(result).hasSize(2)
        assertThat(result[0]).isEqualTo("Banco do Brasil")
        assertThat(result[1]).isEqualTo("Banco Santander")
    }

    @Test
    fun `searchBacenInstitutionsAutocomplete throws exception when query is blank`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.searchBacenInstitutionsAutocomplete("  ")
            }
        assertThat(ex.message).contains("Query cannot be empty for autocomplete search")
    }

    @Test
    fun `searchBacenInstitutionsAutocomplete returns empty list when no quarter available`() {
        whenever(quarterService.getLatestQuarter()).thenReturn(null)

        val result = service.searchBacenInstitutionsAutocomplete("Banco")

        assertThat(result).isEmpty()
    }

    @Test
    fun `searchBacenInstitutionsAutocomplete uses provided anoMes when specified`() {
        whenever(
            institutionSearchRepository.searchBacenInstitutionsAutocomplete(any(), any(), any()),
        ).thenReturn(listOf("A"))

        service.searchBacenInstitutionsAutocomplete("q", anoMes = "202402", limit = 20)

        verify(institutionSearchRepository).searchBacenInstitutionsAutocomplete(
            eq("q"),
            eq("202402"),
            eq(20),
        )
    }

    @Test
    fun `searchBacenInstitutionsAutocomplete uses latest quarter when anoMes is null`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.searchBacenInstitutionsAutocomplete(any(), any(), any()),
        ).thenReturn(emptyList())

        service.searchBacenInstitutionsAutocomplete("q", anoMes = null)

        verify(quarterService).getLatestQuarter()
        verify(institutionSearchRepository).searchBacenInstitutionsAutocomplete(
            eq("q"),
            eq("202403"),
            any(),
        )
    }

    @Test
    fun `bacenInstitutions returns connection with results`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.bacenInstitutions(
                eq(null),
                eq("202403"),
                eq(21),
                isNull(),
            ),
        ).thenReturn(listOf(institutionSearchResult("001", "Banco do Brasil")))
        whenever(
            institutionSearchRepository.countBacenInstitutions(eq(null), eq("202403")),
        ).thenReturn(1L)

        val connection = service.bacenInstitutions(null, null, 20, null)

        assertThat(connection.edges).hasSize(1)
        assertThat(connection.edges[0].node.id).isEqualTo("001")
        assertThat(connection.edges[0].node.name).isEqualTo("Banco do Brasil")
        assertThat(connection.totalCount).isEqualTo(1L)
    }

    @Test
    fun `bacenInstitutions returns empty connection when no quarter available`() {
        whenever(quarterService.getLatestQuarter()).thenReturn(null)

        val connection = service.bacenInstitutions(null, null, 20, null)

        assertThat(connection.edges).isEmpty()
        assertThat(connection.totalCount).isEqualTo(0L)
    }

    @Test
    fun `bacenInstitutions validates page size`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.bacenInstitutions(null, null, 101, null)
            }
        assertThat(ex.message).contains("Parameter 'first' must be between 1 and 100")
    }

    @Test
    fun `bacenInstitutions throws exception for invalid cursor format`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.bacenInstitutions(null, null, 20, "not-valid-base64!!!")
            }
        assertThat(ex.message).contains("Invalid")
        assertThat(ex.message).contains("cursor")
    }

    @Test
    fun `bacenInstitutions correctly calculates hasMore`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.bacenInstitutions(
                eq(null),
                eq("202403"),
                eq(3),
                isNull(),
            ),
        ).thenReturn(
            listOf(
                institutionSearchResult("001", "A"),
                institutionSearchResult("033", "B"),
                institutionSearchResult("104", "C"),
            ),
        )
        whenever(
            institutionSearchRepository.countBacenInstitutions(eq(null), eq("202403")),
        ).thenReturn(3L)

        val connection = service.bacenInstitutions(null, null, 2, null)

        assertThat(connection.edges).hasSize(2)
        assertThat(connection.pageInfo.hasNextPage).isTrue()
    }

    @Test
    fun `bacenInstitutions maps InstitutionSearchResult to BacenInstitutionSummary`() {
        val assetId = UUID.randomUUID()
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.bacenInstitutions(
                eq(null),
                eq("202403"),
                eq(21),
                isNull(),
            ),
        ).thenReturn(
            listOf(
                InstitutionSearchResult(
                    bacenCodInst = "001",
                    nomeInstituicao = "Banco do Brasil",
                    cnpjInstituicaoLider = null,
                    legalName = "Banco do Brasil S.A.",
                    tradeName = "BB",
                    assetId = assetId,
                    profit = BigDecimal("100.50"),
                    equity = BigDecimal("500.75"),
                    equityYearAgo = BigDecimal("450.00"),
                    yoyEquityGrowth = BigDecimal("11.28"),
                    basileiaIndex = BigDecimal("12.5"),
                    bacenInstitutionType = 1,
                    tipoConsolidacao = "I",
                    codConglomeradoPrudencial = null,
                    codConglomeradoFinanceiro = null,
                    tipoConsolidadoBancario = "B1",
                    institutionType = BacenInstitutionType.INDIVIDUAL_INSTITUTION,
                ),
            ),
        )
        whenever(
            institutionSearchRepository.countBacenInstitutions(eq(null), eq("202403")),
        ).thenReturn(1L)

        val connection = service.bacenInstitutions(null, null, 20, null)

        val node = connection.edges[0].node
        assertThat(node.id).isEqualTo("001")
        assertThat(node.name).isEqualTo("Banco do Brasil")
        assertThat(node.legalName).isEqualTo("Banco do Brasil S.A.")
        assertThat(node.tradeName).isEqualTo("BB")
        assertThat(node.assetId).isEqualTo(assetId)
        assertThat(node.profit).isEqualByComparingTo(BigDecimal("100.50"))
        assertThat(node.equity).isEqualByComparingTo(BigDecimal("500.75"))
        assertThat(node.yoyEquityGrowth).isEqualByComparingTo(BigDecimal("11.28"))
        assertThat(node.basileiaIndex).isEqualByComparingTo(BigDecimal("12.5"))
        assertThat(node.bacenInstitutionType).isEqualTo(1)
        assertThat(node.institutionType).isEqualTo(BacenInstitutionType.INDIVIDUAL_INSTITUTION)
        assertThat(node.tipoConsolidadoBancario).isEqualTo("B1")
    }

    @Test
    fun `bacenInstitutions encodes cursor correctly`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.bacenInstitutions(
                eq(null),
                eq("202403"),
                eq(21),
                isNull(),
            ),
        ).thenReturn(listOf(institutionSearchResult("001", "A")))
        whenever(
            institutionSearchRepository.countBacenInstitutions(eq(null), eq("202403")),
        ).thenReturn(1L)

        val connection = service.bacenInstitutions(null, null, 20, null)

        val cursor = connection.edges[0].cursor
        assertThat(cursor).isNotBlank()
        val decoded = CursorEncoder.decodeCompositeCursorToString(cursor)
        assertThat(decoded.id).isEqualTo("001")
    }

    @Test
    fun `bacenInstitutions normalizes blank query to null`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.bacenInstitutions(
                eq(null),
                eq("202403"),
                eq(21),
                isNull(),
            ),
        ).thenReturn(emptyList())
        whenever(
            institutionSearchRepository.countBacenInstitutions(eq(null), eq("202403")),
        ).thenReturn(0L)

        service.bacenInstitutions("  ", null, 20, null)

        verify(institutionSearchRepository).bacenInstitutions(
            eq(null),
            eq("202403"),
            eq(21),
            isNull(),
        )
    }

    @Test
    fun `bacenInstitutionSlugs returns connection with results`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.listInstitutionSlugs(
                eq("202403"),
                eq(21),
                isNull(),
            ),
        ).thenReturn(
            listOf(
                InstitutionSlugResult("001", "Banco do Brasil", Instant.parse("2024-01-15T10:00:00Z")),
            ),
        )

        val connection = service.bacenInstitutionSlugs(null, 20, null)

        assertThat(connection.edges).hasSize(1)
        assertThat(connection.edges[0].node.bacenCodInst).isEqualTo("001")
        assertThat(connection.edges[0].node.nomeInstituicao).isEqualTo("Banco do Brasil")
        assertThat(connection.edges[0].node.updatedAt).isEqualTo(Instant.parse("2024-01-15T10:00:00Z"))
        assertThat(connection.pageInfo.hasNextPage).isFalse()
    }

    @Test
    fun `bacenInstitutionSlugs returns empty connection when no quarter available`() {
        whenever(quarterService.getLatestQuarter()).thenReturn(null)

        val connection = service.bacenInstitutionSlugs(null, 20, null)

        assertThat(connection.edges).isEmpty()
        assertThat(connection.pageInfo.hasNextPage).isFalse()
    }

    @Test
    fun `bacenInstitutionSlugs validates page size`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.bacenInstitutionSlugs(null, 101, null)
            }
        assertThat(ex.message).contains("Parameter 'first' must be between 1 and 100")
    }

    @Test
    fun `bacenInstitutionSlugs correctly calculates hasMore`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(
            institutionSearchRepository.listInstitutionSlugs(
                eq("202403"),
                eq(3),
                isNull(),
            ),
        ).thenReturn(
            listOf(
                InstitutionSlugResult("001", "A", null),
                InstitutionSlugResult("002", "B", null),
                InstitutionSlugResult("003", "C", null),
            ),
        )

        val connection = service.bacenInstitutionSlugs(null, 2, null)

        assertThat(connection.edges).hasSize(2)
        assertThat(connection.pageInfo.hasNextPage).isTrue()
    }

    private fun institutionSearchResult(
        bacenCodInst: String,
        nomeInstituicao: String,
    ): InstitutionSearchResult =
        InstitutionSearchResult(
            bacenCodInst = bacenCodInst,
            nomeInstituicao = nomeInstituicao,
            cnpjInstituicaoLider = null,
            legalName = null,
            tradeName = null,
            assetId = null,
            profit = null,
            equity = null,
            equityYearAgo = null,
            yoyEquityGrowth = null,
            basileiaIndex = null,
            bacenInstitutionType = null,
            tipoConsolidacao = null,
            codConglomeradoPrudencial = null,
            codConglomeradoFinanceiro = null,
            tipoConsolidadoBancario = null,
            institutionType = null,
        )
}
