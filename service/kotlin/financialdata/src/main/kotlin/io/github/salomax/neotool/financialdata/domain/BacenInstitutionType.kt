package io.github.salomax.neotool.financialdata.domain

/**
 * Enum representing the type of BACEN institution.
 * 
 * Classification logic (priority order):
 * 1. FINANCIAL_GROUP: bacen_cod_inst = cod_conglomerado_financeiro
 * 2. PRUDENTIAL_GROUP: bacen_cod_inst = cod_conglomerado_prudencial (and not financial)
 * 3. INDIVIDUAL_INSTITUTION: tipo_consolidacao = 'I' AND bacen_cod_inst <> cod_conglomerado_prudencial
 */
enum class BacenInstitutionType {
    /**
     * Individual Institutions (Instituições Individuais)
     * Condition: tipo_consolidacao = 'I' AND bacen_cod_inst <> cod_conglomerado_prudencial
     */
    INDIVIDUAL_INSTITUTION,

    /**
     * Prudential Group (Conglomerado Prudencial)
     * Condition: bacen_cod_inst = cod_conglomerado_prudencial (and not financial)
     */
    PRUDENTIAL_GROUP,

    /**
     * Financial Group (Conglomerado Financeiro)
     * Condition: bacen_cod_inst = cod_conglomerado_financeiro
     */
    FINANCIAL_GROUP,
}

