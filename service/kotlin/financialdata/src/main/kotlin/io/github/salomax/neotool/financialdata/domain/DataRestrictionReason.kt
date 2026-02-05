package io.github.salomax.neotool.financialdata.domain

/**
 * Reason code for institution financial data restriction.
 * Extensible for future values (e.g. LEGAL_REQUIREMENT, DATA_QUALITY_ISSUE).
 */
enum class DataRestrictionReason {
    /** Institution formally requested removal of their financial data. */
    INSTITUTION_REQUEST,
}
