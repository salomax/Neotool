package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType

/**
 * Row from the popular-institution resolution query: one popular institution
 * with resolved bacen_cod_inst and FI data for a given quarter.
 * Used to build [BacenInstitutionSummary] (with null metrics) for the discovery carousel.
 */
data class PopularInstitutionResolvedRow(
    val rank: Int,
    val shortDescription: String,
    val bacenCodInst: String,
    val nomeInstituicao: String?,
    val cnpjInstituicaoLider: String?,
    val legalName: String?,
    val tradeName: String?,
    val assetId: java.util.UUID?,
    val bacenInstitutionType: Int?,
    val tipoConsolidacao: String?,
    val codConglomeradoPrudencial: String?,
    val codConglomeradoFinanceiro: String?,
    val tipoConsolidadoBancario: String?,
    val institutionType: BacenInstitutionType?,
)
