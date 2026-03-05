package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.domain.DataRestriction
import io.github.salomax.neotool.financialdata.domain.PopularInstitution
import io.github.salomax.neotool.financialdata.domain.PopularInstitutionTopMetric
import io.github.salomax.neotool.financialdata.domain.PopularInstitutionTopMetricItem
import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric
import io.github.salomax.neotool.financialdata.repo.PopularInstitutionCustomRepository
import io.github.salomax.neotool.financialdata.repo.TopInstitutionRankingRepository
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Service for the institution discovery dashboard: popular institutions carousel
 * with resolved institution summary and top-metric rankings (PROFIT, BASILEIA_INDEX,
 * MOBILIZATION_INDEX) per institution.
 */
@TraceExecution
@Singleton
open class InstitutionDiscoveryService(
    private val popularInstitutionCustomRepository: PopularInstitutionCustomRepository,
    private val topInstitutionRankingRepository: TopInstitutionRankingRepository,
    private val quarterService: QuarterService,
    private val restrictionService: InstitutionDataRestrictionService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Limit used when fetching top rankings per metric to find each popular institution's rank.
     * Institutions outside this range will have null rank/value for that metric.
     */
    private val topMetricsFetchLimit = 100

    /**
     * Returns popular institutions (editorial list) with resolved institution summary and
     * top-metric rankings for the given quarter. Fetches popular list and all three metric
     * rankings in one flow, then assembles per-institution metrics.
     * If [anoMes] is null, uses the latest quarter globally.
     */
    open fun getPopularInstitutions(anoMes: String?): List<PopularInstitution> {
        val targetAnoMes = anoMes ?: quarterService.getLatestQuarter()
        if (targetAnoMes == null) {
            logger.debug { "No quarter available for popular institutions" }
            return emptyList()
        }

        val rows = popularInstitutionCustomRepository.findPopularWithResolvedInstitutions(targetAnoMes)
        if (rows.isEmpty()) return emptyList()
        val (year, month) = parseYearMonth(targetAnoMes)

        val popularBacenCodInsts = rows.map { it.bacenCodInst }.toSet()

        val metricRankingsByMetricAndInstitution =
            topInstitutionRankingRepository
                .findTopMetricsForInstitutions(
                    anoMes = targetAnoMes,
                    bacenCodInsts = popularBacenCodInsts,
                    limit = topMetricsFetchLimit,
                ).associateBy { it.metric to it.bacenCodInst }

        val restrictions = restrictionService.getRestrictionsForInstitutions(rows.map { it.bacenCodInst })

        return rows.map { row ->
            val restriction = restrictions[row.bacenCodInst] ?: DataRestriction.NOT_RESTRICTED
            val isRestricted = restriction.isRestricted
            val name = row.nomeInstituicao ?: row.tradeName ?: row.legalName ?: "Unknown"
            val summary =
                BacenInstitutionSummary(
                    id = row.bacenCodInst,
                    name = name,
                    legalName = if (isRestricted) null else row.legalName,
                    tradeName = if (isRestricted) null else row.tradeName,
                    assetId = row.assetId,
                    profit = null,
                    equity = null,
                    yoyEquityGrowth = null,
                    basileiaIndex = null,
                    bacenInstitutionType = if (isRestricted) null else row.bacenInstitutionType,
                    institutionType = if (isRestricted) null else row.institutionType,
                    tipoConsolidadoBancario = if (isRestricted) null else row.tipoConsolidadoBancario,
                    dataRestriction = restriction,
                )

            val popularInstitutionTopMetrics =
                TopInstitutionMetric.entries.map { metric ->
                    val metricRow = metricRankingsByMetricAndInstitution[metric to row.bacenCodInst]
                    val rank = metricRow?.rank ?: 0
                    val metricValue = if (isRestricted) null else metricRow?.metricValue
                    PopularInstitutionTopMetric(
                        metric = metric,
                        popularInstitutionTopMetricItem =
                            PopularInstitutionTopMetricItem(
                                rank = rank,
                                institution = summary,
                                metricValue = metricValue,
                            ),
                    )
                }

            PopularInstitution(
                rank = row.rank,
                shortDescription = row.shortDescription,
                quarter = targetAnoMes,
                year = year,
                month = month,
                institution = summary,
                popularInstitutionTopMetrics = popularInstitutionTopMetrics,
            )
        }
    }

    private fun parseYearMonth(anoMes: String): Pair<Int?, Int?> {
        if (anoMes.length != 6 || anoMes.any { !it.isDigit() }) {
            return null to null
        }

        val year = anoMes.substring(0, 4).toIntOrNull()
        val month = anoMes.substring(4, 6).toIntOrNull()
        return year to month
    }
}
