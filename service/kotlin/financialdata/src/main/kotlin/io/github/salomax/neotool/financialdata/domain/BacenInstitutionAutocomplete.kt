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
    // Derived from financial_institution.logo_storage_key (if linked)
    val logoUrl: String?,
)
