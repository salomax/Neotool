package io.github.salomax.neotool.financialdata.domain.fixedincome

/**
 * Product group for the 2×3 equivalent-rate grid.
 * Grouped by income-tax regime, since formulas are equivalent inside each tax class.
 */
enum class ProductGroup {
    TAXABLE_IR,
    TAXABLE_IR_NO_IOF,
    EXEMPT_IR,
}

fun productGroup(product: FixedIncomeProduct): ProductGroup =
    when (product) {
        FixedIncomeProduct.CDB,
        FixedIncomeProduct.LC,
        FixedIncomeProduct.DEBENTURE,
        -> ProductGroup.TAXABLE_IR
        FixedIncomeProduct.LCI,
        FixedIncomeProduct.LCA,
        FixedIncomeProduct.FREE_TAX_DEBENTURE,
        -> ProductGroup.EXEMPT_IR
    }

fun defaultProductForGroup(group: ProductGroup): FixedIncomeProduct =
    when (group) {
        ProductGroup.TAXABLE_IR -> FixedIncomeProduct.CDB
        ProductGroup.TAXABLE_IR_NO_IOF -> FixedIncomeProduct.CDB
        ProductGroup.EXEMPT_IR -> FixedIncomeProduct.LCI
    }
