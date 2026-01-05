package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object for BACEN institution autocomplete suggestions.
 * Used for real-time search suggestions as user types.
 */
data class BacenInstitutionAutocomplete(
    val id: String, // bacen_cod_inst
    val name: String, // nome_instituicao from bacen_institution
    val cnpj: String?, // cnpj_instituicao_lider (if available)
    val legalName: String?, // legal_name from financial_institution
    val tradeName: String?, // trade_name from financial_institution
    val logoUrl: String?, // Derived from financial_institution.logo_storage_key (if linked)
)

