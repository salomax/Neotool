package io.github.salomax.neotool.financialdata.domain

/**
 * Financial metrics used for ranking institutions in the discovery dashboard.
 * Matches GraphQL enum TopInstitutionMetric.
 */
enum class TopInstitutionMetric {
    /** Net Profit (Lucro Líquido) */
    PROFIT,

    /** Capital Adequacy Index (Basileia III) */
    BASILEIA_INDEX,

    /** Mobilization Index (Índice de Imobilização) */
    MOBILIZATION_INDEX,
}
