package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing a quarter range for filtering reports.
 * Supports both absolute (from/to) and relative (last N quarters) ranges.
 */
data class QuarterRange(
    /**
     * Start quarter in "YYYYMM" format (inclusive)
     * Used when from/to is specified
     */
    val from: String? = null,
    /**
     * End quarter in "YYYYMM" format (inclusive)
     * Used when from/to is specified
     */
    val to: String? = null,
    /**
     * Number of most recent quarters to include
     * Used when last is specified
     */
    val last: Int? = null,
) {
    init {
        require(
            (from != null && to != null) || last != null,
        ) { "Either (from and to) or last must be specified" }
        require(
            (from == null && to == null) || last == null,
        ) { "Cannot specify both (from/to) and last" }
        if (last != null) {
            require(last > 0) { "last must be greater than 0" }
        }
    }
}
