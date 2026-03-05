package io.github.salomax.neotool.financialdata.graphql.resolver

import io.github.salomax.neotool.financialdata.graphql.dto.PopularInstitutionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PopularInstitutionTopMetricDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PopularInstitutionTopMetricItemDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.InstitutionSearchMapper
import io.github.salomax.neotool.financialdata.service.InstitutionDiscoveryService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for institution discovery dashboard: popularInstitutions query
 * with top metrics (PROFIT, BASILEIA_INDEX, MOBILIZATION_INDEX) per institution.
 */
@Singleton
class InstitutionDiscoveryResolver(
    private val institutionDiscoveryService: InstitutionDiscoveryService,
    private val mapper: InstitutionSearchMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Returns the editorial list of popular institutions with resolved summary and
     * top-metric rankings for the carousel.
     *
     * @param anoMes Quarter in YYYYMM format. If null, uses latest quarter globally.
     */
    fun popularInstitutions(anoMes: String?): List<PopularInstitutionDTO> {
        val list = institutionDiscoveryService.getPopularInstitutions(anoMes)
        return list.map { popular ->
            PopularInstitutionDTO(
                rank = popular.rank,
                shortDescription = popular.shortDescription,
                quarter = popular.quarter,
                year = popular.year,
                month = popular.month,
                institution = mapper.toBacenInstitutionSummaryItemDTO(popular.institution),
                popularInstitutionTopMetrics =
                    popular.popularInstitutionTopMetrics.map { topMetric ->
                        PopularInstitutionTopMetricDTO(
                            metric = topMetric.metric,
                            popularInstitutionTopMetricItem =
                                PopularInstitutionTopMetricItemDTO(
                                    rank = topMetric.popularInstitutionTopMetricItem.rank,
                                    institution =
                                        mapper.toBacenInstitutionSummaryItemDTO(
                                            topMetric.popularInstitutionTopMetricItem.institution,
                                        ),
                                    metricValue =
                                        topMetric.popularInstitutionTopMetricItem.metricValue?.toDouble(),
                                ),
                        )
                    },
            )
        }
    }
}
