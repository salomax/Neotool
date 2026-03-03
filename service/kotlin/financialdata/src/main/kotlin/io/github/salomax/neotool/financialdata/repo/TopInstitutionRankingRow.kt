package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import java.math.BigDecimal

/**
 * Result row from top-institution ranking query.
 * Carries institution identification and one metric value; can be mapped to
 * [InstitutionSearchResult] for building [io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary].
 */
data class TopInstitutionRankingRow(
    val bacenCodInst: String,
    val nomeInstituicao: String?,
    val cnpjInstituicaoLider: String?,
    val legalName: String?,
    val tradeName: String?,
    val assetId: java.util.UUID?,
    /** The metric value for this row (profit, basileia index, or mobilization index). */
    val metricValue: BigDecimal?,
    val bacenInstitutionType: Int?,
    val tipoConsolidacao: String?,
    val codConglomeradoPrudencial: String?,
    val codConglomeradoFinanceiro: String?,
    val tipoConsolidadoBancario: String?,
    val institutionType: BacenInstitutionType?,
)
