package io.github.salomax.neotool.financialdata.domain.fixedincome

/**
 * Fixed income product type.
 * Tax: CDB, LC, DEBENTURE → taxable; LCI, LCA, FREE_TAX_DEBENTURE → exempt.
 * The simulator may also synthesize a taxable-without-IOF comparison lane
 * (TAXABLE_IR_NO_IOF) for committed products.
 */
enum class FixedIncomeProduct {
    CDB,
    LC,
    LCI,
    LCA,
    DEBENTURE,
    FREE_TAX_DEBENTURE,
}
