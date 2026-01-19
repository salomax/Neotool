package io.github.salomax.neotool.financialdata.domain

/**
 * Enum representing the bank type of a BACEN institution.
 * Maps from tipo_consolidado_bancario field in bacen_institution table.
 */
enum class BacenInstitutionBankType {
    /**
     * B1 - Commercial bank, multiple bank with commercial portfolio, or savings banks
     */
    B1,

    /**
     * B2 - Multiple bank without commercial portfolio or foreign exchange/investment bank
     */
    B2,

    /**
     * B3S - Singular credit cooperative
     */
    B3S,

    /**
     * B3C - Central and confederation of credit cooperatives
     */
    B3C,

    /**
     * B4 - Development bank
     */
    B4,

    /**
     * N1 - Non-bank credit
     */
    N1,

    /**
     * N2 - Non-bank capital markets
     */
    N2,

    /**
     * N4 - Payment institutions
     */
    N4,
}
