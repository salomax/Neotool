package io.github.salomax.neotool.financialdata.domain

/**
 * Enum representing the control type of a BACEN institution.
 * Maps from tipo_controle field in bacen_institution table.
 */
enum class BacenControlType {
    /**
     * Public (1) - Public institution
     */
    PUBLIC,

    /**
     * Private Domestic (2) - Domestic private institution
     */
    PRIVATE_DOMESTIC,

    /**
     * Private with Foreign Control (3) - Private institution with foreign control
     */
    PRIVATE_FOREIGN_CONTROL,
}
