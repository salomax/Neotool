package io.github.salomax.neotool.banking.domain.investment

/**
 * BR-P2-5: Tesouro Direto must not set [Holding.couponDayOfMonth] (system calendar).
 * Private credit with coupon frequency other than [CouponFrequency.AT_MATURITY] must set day 1–31.
 */
object CouponDayOfMonthRules {
    val TESOURO_ASSET_TYPES: Set<AssetType> = setOf(AssetType.TREASURY)

    fun validate(
        assetType: AssetType,
        holding: Holding,
    ) {
        val freq = holding.couponFrequency
        val day = holding.couponDayOfMonth

        if (freq == null || freq == CouponFrequency.AT_MATURITY) {
            require(day == null) {
                "couponDayOfMonth must be null when coupon frequency is unset or AT_MATURITY"
            }
            return
        }

        if (assetType in TESOURO_ASSET_TYPES) {
            require(day == null) {
                "Tesouro Direto holdings must not set couponDayOfMonth (system-defined payment calendar)"
            }
            return
        }

        require(day != null && day in 1..31) {
            "couponDayOfMonth (1–31) is required for coupon-bearing instruments that use a private settlement calendar"
        }
    }
}
