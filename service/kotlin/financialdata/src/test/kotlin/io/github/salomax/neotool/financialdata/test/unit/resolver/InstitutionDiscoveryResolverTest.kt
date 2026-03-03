package io.github.salomax.neotool.financialdata.test.unit.resolver

import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.domain.PopularInstitution
import io.github.salomax.neotool.financialdata.domain.PopularInstitutionTopMetric
import io.github.salomax.neotool.financialdata.domain.PopularInstitutionTopMetricItem
import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSummaryItemDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.InstitutionSearchMapper
import io.github.salomax.neotool.financialdata.graphql.resolver.InstitutionDiscoveryResolver
import io.github.salomax.neotool.financialdata.service.InstitutionDiscoveryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InstitutionDiscoveryResolverTest {
    private val institutionDiscoveryService: InstitutionDiscoveryService = mock()
    private val mapper: InstitutionSearchMapper = mock()
    private lateinit var resolver: InstitutionDiscoveryResolver

    @BeforeEach
    fun setUp() {
        resolver = InstitutionDiscoveryResolver(institutionDiscoveryService, mapper)
    }

    @Test
    fun `popularInstitutions returns DTO list with top metrics`() {
        val summary =
            BacenInstitutionSummary(
                id = "001",
                name = "Bank A",
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
        val topMetrics =
            TopInstitutionMetric.entries.map { metric ->
                PopularInstitutionTopMetric(
                    metric = metric,
                    popularInstitutionTopMetricItem =
                        PopularInstitutionTopMetricItem(
                            rank = 1,
                            institution = summary,
                            metricValue = null,
                        ),
                )
            }
        whenever(institutionDiscoveryService.getPopularInstitutions(eq(null)))
            .thenReturn(
                listOf(
                    PopularInstitution(
                        rank = 1,
                        shortDescription = "Desc",
                        quarter = "202403",
                        year = 2024,
                        month = 3,
                        institution = summary,
                        popularInstitutionTopMetrics = topMetrics,
                    ),
                ),
            )
        whenever(mapper.toBacenInstitutionSummaryItemDTO(any()))
            .thenReturn(
                BacenInstitutionSummaryItemDTO(
                    id = "001",
                    name = "Bank A",
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
                    dataRestriction = null,
                ),
            )

        val result = resolver.popularInstitutions(null)

        assertThat(result).hasSize(1)
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(result[0].shortDescription).isEqualTo("Desc")
        assertThat(result[0].quarter).isEqualTo("202403")
        assertThat(result[0].year).isEqualTo(2024)
        assertThat(result[0].month).isEqualTo(3)
        assertThat(result[0].institution.name).isEqualTo("Bank A")
        assertThat(result[0].popularInstitutionTopMetrics).hasSize(3)
        verify(institutionDiscoveryService).getPopularInstitutions(null)
    }
}
