package io.github.salomax.neotool.financialdata.domain.fixedincome

/**
 * Fixed income product type.
 * Tax: CDB, LC, DEBENTURE → taxable; LCI, LCA, FREE_TAX_DEBENTURE → exempt.
 * Calculation characteristics are identical within each tax group (TAXABLE_IR / EXEMPT_IR).
 */
enum class FixedIncomeProduct {
    CDB,
    LC,
    LCI,
    LCA,
    DEBENTURE,
    FREE_TAX_DEBENTURE,
}
