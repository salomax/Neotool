package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.FundTaxProfile

internal object FundTaxProfileClassifier {
    private val exemptNameMarkers =
        listOf(
            "INCENTIVAD",
            "INFRA",
            "INFRAESTRUTURA",
            "DEBENTURE INCENTIVADA",
            "DEBENTURES INCENTIVADAS",
            "LEI 12 431",
            "LEI 12431",
            "FI INFRA",
            "FIINFRA",
        )

    fun taxProfileFor(
        assetType: String,
        fundName: String? = null,
        rawTaxMaturity: String? = null,
    ): FundTaxProfile {
        val normalizedType = assetType.trim().uppercase()
        val normalizedName = fundName.normalizeFundName()
        val normalizedRawTaxMaturity = rawTaxMaturity.normalizeRawTaxMaturity()

        return when {
            normalizedType in setOf("PGBL", "VGBL") -> FundTaxProfile.PENSION
            normalizedType == "EQUITY_FUND" -> FundTaxProfile.VARIABLE_INCOME_15
            normalizedType in
                setOf(
                    "FIXED_INCOME_FUND",
                    "MULTI_MARKET_FUND",
                    "CURRENCY_FUND",
                    "FOREIGN_EXCHANGE_FUND",
                )
            -> {
                when {
                    looksTaxExempt(normalizedName) -> FundTaxProfile.UNKNOWN
                    normalizedRawTaxMaturity == "S" -> FundTaxProfile.COME_COTAS_LONG
                    normalizedRawTaxMaturity == "N" -> FundTaxProfile.COME_COTAS_SHORT
                    normalizedName.contains("CURTO PRAZO") -> FundTaxProfile.COME_COTAS_SHORT
                    else -> FundTaxProfile.COME_COTAS_LONG
                }
            }
            else -> FundTaxProfile.UNKNOWN
        }
    }

    private fun looksTaxExempt(normalizedName: String): Boolean =
        normalizedName.isNotBlank() && exemptNameMarkers.any(normalizedName::contains)

    private fun String?.normalizeFundName(): String =
        this
            ?.uppercase()
            ?.replace(Regex("[^A-Z0-9]+"), " ")
            ?.trim()
            .orEmpty()

    private fun String?.normalizeRawTaxMaturity(): String? =
        this
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
}
