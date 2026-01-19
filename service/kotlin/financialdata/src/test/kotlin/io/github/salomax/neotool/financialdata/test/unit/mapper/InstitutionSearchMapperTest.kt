package io.github.salomax.neotool.financialdata.test.unit.mapper

import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.graphql.mapper.InstitutionSearchMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class InstitutionSearchMapperTest {
    private val mapper = InstitutionSearchMapper()

    @Test
    fun `should map autocomplete suggestions`() {
        val suggestions = listOf("Banco do Brasil", "Banco Santander")

        val dto = mapper.toBacenInstitutionAutocompleteConnectionDTO(suggestions)

        assertThat(dto.suggestions).hasSize(2)
        assertThat(dto.suggestions[0].name).isEqualTo("Banco do Brasil")
        assertThat(dto.suggestions[1].name).isEqualTo("Banco Santander")
    }

    @Test
    fun `should map institution connection to dto`() {
        val assetId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val summary =
            BacenInstitutionSummary(
                id = "001",
                name = "Banco do Brasil",
                legalName = "Banco do Brasil S.A.",
                tradeName = "BB",
                assetId = assetId,
                profit = BigDecimal("123.45"),
                equity = BigDecimal("678.90"),
                yoyEquityGrowth = BigDecimal("5.5"),
                basileiaIndex = BigDecimal("12.3"),
                bacenInstitutionType = 1,
                institutionType = BacenInstitutionType.INDIVIDUAL_INSTITUTION,
                tipoConsolidadoBancario = "B1",
            )

        val connection =
            ConnectionBuilder.buildConnection(
                items = listOf(summary),
                hasMore = false,
                encodeCursor = { "cursor-${it.id}" },
                totalCount = 1,
            )

        val dto = mapper.toBacenInstitutionConnectionDTO(connection)

        assertThat(dto.totalCount).isEqualTo(1)
        assertThat(dto.edges).hasSize(1)
        assertThat(dto.edges[0].cursor).isEqualTo("cursor-001")
        assertThat(dto.edges[0].node.id).isEqualTo("001")
        assertThat(dto.edges[0].node.assetId).isEqualTo(assetId.toString())
        assertThat(dto.edges[0].node.yoyEquityGrowth).isEqualTo(5.5)
        assertThat(dto.edges[0].node.institutionType).isEqualTo(BacenInstitutionType.INDIVIDUAL_INSTITUTION)
    }

    @Test
    fun `toBacenInstitutionAutocompleteConnectionDTO handles empty list`() {
        val dto = mapper.toBacenInstitutionAutocompleteConnectionDTO(emptyList())

        assertThat(dto.suggestions).isEmpty()
    }

    @Test
    fun `toBacenInstitutionConnectionDTO handles empty edges`() {
        val connection =
            ConnectionBuilder.buildConnection(
                items = emptyList<BacenInstitutionSummary>(),
                hasMore = false,
                encodeCursor = { _ -> "" },
                totalCount = 0L,
            )

        val dto = mapper.toBacenInstitutionConnectionDTO(connection)

        assertThat(dto.edges).isEmpty()
    }

    @Test
    fun `toBacenInstitutionSummaryItemDTO handles null optional fields`() {
        val summary =
            BacenInstitutionSummary(
                id = "001",
                name = "A",
                legalName = null,
                tradeName = null,
                assetId = null,
                profit = null,
                equity = null,
                yoyEquityGrowth = null,
                basileiaIndex = null,
                bacenInstitutionType = null,
                institutionType = null,
                tipoConsolidadoBancario = null,
            )

        val dto = mapper.toBacenInstitutionSummaryItemDTO(summary)

        assertThat(dto.id).isEqualTo("001")
        assertThat(dto.name).isEqualTo("A")
        assertThat(dto.legalName).isNull()
        assertThat(dto.tradeName).isNull()
        assertThat(dto.assetId).isNull()
        assertThat(dto.profit).isNull()
        assertThat(dto.equity).isNull()
        assertThat(dto.yoyEquityGrowth).isNull()
        assertThat(dto.basileiaIndex).isNull()
        assertThat(dto.bacenInstitutionType).isNull()
        assertThat(dto.institutionType).isNull()
        assertThat(dto.tipoConsolidadoBancario).isNull()
    }

    @Test
    fun `toBacenInstitutionSummaryItemDTO converts BigDecimal to Double correctly`() {
        val summary =
            BacenInstitutionSummary(
                id = "001",
                name = "A",
                legalName = null,
                tradeName = null,
                assetId = null,
                profit = BigDecimal("123.45"),
                equity = BigDecimal("678.90"),
                yoyEquityGrowth = null,
                basileiaIndex = null,
                bacenInstitutionType = null,
                institutionType = null,
                tipoConsolidadoBancario = null,
            )

        val dto = mapper.toBacenInstitutionSummaryItemDTO(summary)

        assertThat(dto.profit).isEqualTo(123.45)
        assertThat(dto.equity).isEqualTo(678.90)
    }
}
