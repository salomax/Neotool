package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.DataRestriction
import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric
import io.github.salomax.neotool.financialdata.repo.PopularInstitutionCustomRepository
import io.github.salomax.neotool.financialdata.repo.PopularInstitutionResolvedRow
import io.github.salomax.neotool.financialdata.repo.TopInstitutionMetricRankRow
import io.github.salomax.neotool.financialdata.repo.TopInstitutionRankingRepository
import io.github.salomax.neotool.financialdata.service.InstitutionDataRestrictionService
import io.github.salomax.neotool.financialdata.service.InstitutionDiscoveryService
import io.github.salomax.neotool.financialdata.service.QuarterService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class InstitutionDiscoveryServiceTest {
    private val popularInstitutionCustomRepository: PopularInstitutionCustomRepository = mock()
    private val topInstitutionRankingRepository: TopInstitutionRankingRepository = mock()
    private val quarterService: QuarterService = mock()
    private val restrictionService: InstitutionDataRestrictionService = mock()
    private lateinit var service: InstitutionDiscoveryService

    @BeforeEach
    fun setUp() {
        whenever(restrictionService.getRestrictionsForInstitutions(any())).thenReturn(emptyMap())
        whenever(
            topInstitutionRankingRepository.findTopMetricsForInstitutions(any(), any(), any()),
        ).thenReturn(emptyList())
        service =
            InstitutionDiscoveryService(
                popularInstitutionCustomRepository,
                topInstitutionRankingRepository,
                quarterService,
                restrictionService,
            )
    }

    @Test
    fun `getPopularInstitutions returns empty list when no quarter available`() {
        whenever(quarterService.getLatestQuarter()).thenReturn(null)

        val result = service.getPopularInstitutions(null)

        assertThat(result).isEmpty()
        verify(popularInstitutionCustomRepository, never()).findPopularWithResolvedInstitutions(any())
    }

    @Test
    fun `getPopularInstitutions uses latest quarter when anoMes is null`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(popularInstitutionCustomRepository.findPopularWithResolvedInstitutions(eq("202403")))
            .thenReturn(
                listOf(
                    PopularInstitutionResolvedRow(
                        rank = 1,
                        shortDescription = "Desc",
                        bacenCodInst = "001",
                        nomeInstituicao = "Bank A",
                        cnpjInstituicaoLider = null,
                        legalName = "Legal A",
                        tradeName = "Trade A",
                        assetId = null,
                        bacenInstitutionType = 1,
                        tipoConsolidacao = "I",
                        codConglomeradoPrudencial = null,
                        codConglomeradoFinanceiro = null,
                        tipoConsolidadoBancario = null,
                        institutionType = null,
                    ),
                ),
            )

        val result = service.getPopularInstitutions(null)

        assertThat(result).hasSize(1)
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(result[0].shortDescription).isEqualTo("Desc")
        assertThat(result[0].quarter).isEqualTo("202403")
        assertThat(result[0].year).isEqualTo(2024)
        assertThat(result[0].month).isEqualTo(3)
        assertThat(result[0].institution.id).isEqualTo("001")
        assertThat(result[0].institution.name).isEqualTo("Bank A")
        assertThat(result[0].popularInstitutionTopMetrics).hasSize(3)
        verify(quarterService).getLatestQuarter()
    }

    @Test
    fun `getPopularInstitutions includes rank and metricValue when institution is in top list`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(popularInstitutionCustomRepository.findPopularWithResolvedInstitutions(eq("202403")))
            .thenReturn(
                listOf(
                    PopularInstitutionResolvedRow(
                        rank = 1,
                        shortDescription = "Desc",
                        bacenCodInst = "001",
                        nomeInstituicao = "Bank A",
                        cnpjInstituicaoLider = null,
                        legalName = null,
                        tradeName = null,
                        assetId = null,
                        bacenInstitutionType = null,
                        tipoConsolidacao = "I",
                        codConglomeradoPrudencial = null,
                        codConglomeradoFinanceiro = null,
                        tipoConsolidadoBancario = null,
                        institutionType = null,
                    ),
                ),
            )
        whenever(
            topInstitutionRankingRepository.findTopMetricsForInstitutions(
                eq("202403"),
                any(),
                any(),
            ),
        ).thenReturn(
            listOf(
                TopInstitutionMetricRankRow(
                    metric = TopInstitutionMetric.PROFIT,
                    bacenCodInst = "001",
                    rank = 1,
                    metricValue = BigDecimal("1000.5"),
                ),
            ),
        )

        val result = service.getPopularInstitutions("202403")

        val profitMetric =
            result[0].popularInstitutionTopMetrics.find { it.metric == TopInstitutionMetric.PROFIT }
        assertThat(profitMetric).isNotNull
        assertThat(profitMetric!!.popularInstitutionTopMetricItem.rank).isEqualTo(1)
        assertThat(profitMetric.popularInstitutionTopMetricItem.metricValue)
            .isEqualByComparingTo(BigDecimal("1000.5"))
    }

    @Test
    fun `getPopularInstitutions nulls metricValue for restricted institutions`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(popularInstitutionCustomRepository.findPopularWithResolvedInstitutions(eq("202403")))
            .thenReturn(
                listOf(
                    PopularInstitutionResolvedRow(
                        rank = 1,
                        shortDescription = "Desc",
                        bacenCodInst = "001",
                        nomeInstituicao = "Bank A",
                        cnpjInstituicaoLider = null,
                        legalName = null,
                        tradeName = null,
                        assetId = null,
                        bacenInstitutionType = null,
                        tipoConsolidacao = "I",
                        codConglomeradoPrudencial = null,
                        codConglomeradoFinanceiro = null,
                        tipoConsolidadoBancario = null,
                        institutionType = null,
                    ),
                ),
            )
        whenever(
            topInstitutionRankingRepository.findTopMetricsForInstitutions(
                eq("202403"),
                any(),
                any(),
            ),
        ).thenReturn(
            listOf(
                TopInstitutionMetricRankRow(
                    metric = TopInstitutionMetric.BASILEIA_INDEX,
                    bacenCodInst = "001",
                    rank = 1,
                    metricValue = BigDecimal("15.5"),
                ),
            ),
        )
        whenever(restrictionService.getRestrictionsForInstitutions(listOf("001")))
            .thenReturn(mapOf("001" to DataRestriction(isRestricted = true, reason = null)))

        val result = service.getPopularInstitutions("202403")

        val basileiaMetric =
            result[0].popularInstitutionTopMetrics.find {
                it.metric == TopInstitutionMetric.BASILEIA_INDEX
            }
        assertThat(basileiaMetric).isNotNull
        assertThat(basileiaMetric!!.popularInstitutionTopMetricItem.metricValue).isNull()
        assertThat(result[0].institution.dataRestriction.isRestricted).isTrue()
    }
}
