package io.github.salomax.neotool.financialdata.domain.fixedincome

/**
 * Rate model for fixed income simulation.
 * %CDI uses CDI projection + contracted percentage; FIXED uses fixed annual rate; IPCA_PLUS uses IPCA + spread.
 */
enum class RateModel {
    CDI,
    FIXED,
    IPCA_PLUS,
}
