package io.github.salomax.neotool.financialdata.domain

/**
 * Indicates whether an institution's financial data is restricted from exposure.
 * When isRestricted is true, reason explains why.
 */
data class DataRestriction(
    val isRestricted: Boolean,
    val reason: DataRestrictionReason? = null,
) {
    companion object {
        val NOT_RESTRICTED = DataRestriction(isRestricted = false, reason = null)
    }
}
