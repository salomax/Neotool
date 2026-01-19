package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object for BACEN institution autocomplete suggestions.
 * Used for real-time search suggestions as user types.
 */
data class BacenInstitutionAutocomplete(
    // bacen_cod_inst
    val id: String,
    // nome_instituicao from bacen_institution
    val name: String,
    // cnpj_instituicao_lider (if available)
    val cnpj: String?,
    // legal_name from financial_institution
    val legalName: String?,
    // trade_name from financial_institution
    val tradeName: String?,
    // asset_id from financial_institution (if linked) - used for GraphQL federation
    val assetId: java.util.UUID?,
)
