package io.github.salomax.neotool.financialdata.model

import java.math.BigDecimal

/**
 * Defines how indicator values should be normalized for API output.
 *
 * Raw data from different sources may represent percentages differently:
 * - Some sources provide 50 to mean 50%
 * - Others provide 0.5 to mean 50%
 *
 * This enum standardizes all percentage values to 0-1 format (where 0.5 = 50%).
 */
enum class ValueFormat {
    /**
     * Source provides percentage as 0-100 scale (e.g., 6.8 means 6.8%).
     * Values will be divided by 100 for API output.
     */
    PERCENTAGE_0_100,

    /**
     * Source provides percentage as 0-1 scale (e.g., 0.068 means 6.8%).
     * No transformation needed.
     */
    PERCENTAGE_0_1,

    /**
     * Non-percentage value (e.g., BRL amount, count).
     * No transformation needed.
     */
    ABSOLUTE,

    ;

    companion object {
        private val DIVISOR_100 = BigDecimal("100")

        fun fromString(value: String?): ValueFormat =
            when (value?.uppercase()) {
                "PERCENTAGE_0_100" -> PERCENTAGE_0_100
                "PERCENTAGE_0_1" -> PERCENTAGE_0_1
                "ABSOLUTE" -> ABSOLUTE
                else -> ABSOLUTE
            }
    }

    /**
     * Transforms a raw value to the normalized format.
     * For PERCENTAGE_0_100, divides by 100.
     * For other formats, returns the value unchanged.
     */
    fun normalize(value: BigDecimal): BigDecimal =
        when (this) {
            PERCENTAGE_0_100 -> value.divide(DIVISOR_100)
            else -> value
        }
}
