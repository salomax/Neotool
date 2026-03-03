package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric

/**
 * Repository for querying top N institutions ranked by a single financial metric.
 * Used by the institution discovery dashboard for PROFIT, BASILEIA_INDEX, and MOBILIZATION_INDEX.
 */
interface TopInstitutionRankingRepository {
    /**
     * Returns the top [limit] institutions for the given [metric] and [anoMes],
     * ordered by metric value descending (best first).
     * Only active institutions (situacao = 'A') with individual consolidation (tipo_consolidacao = 'I').
     *
     * @param metric Metric to rank by (PROFIT, BASILEIA_INDEX, MOBILIZATION_INDEX)
     * @param anoMes Quarter in YYYYMM format
     * @param limit Max number of results (typically 10, max 20)
     */
    fun findTopByMetric(
        metric: TopInstitutionMetric,
        anoMes: String,
        limit: Int,
    ): List<TopInstitutionRankingRow>

    /**
     * Returns top-rank rows for all discovery metrics (PROFIT, BASILEIA_INDEX, MOBILIZATION_INDEX)
     * for the given [anoMes], filtered to the requested [bacenCodInsts].
     *
     * Ranks are computed globally per metric and then filtered to [bacenCodInsts].
     */
    fun findTopMetricsForInstitutions(
        anoMes: String,
        bacenCodInsts: Collection<String>,
        limit: Int,
    ): List<TopInstitutionMetricRankRow>
}
