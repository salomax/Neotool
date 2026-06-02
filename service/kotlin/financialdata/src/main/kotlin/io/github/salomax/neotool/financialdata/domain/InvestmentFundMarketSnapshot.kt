package io.github.salomax.neotool.financialdata.domain

data class InvestmentFundMarketSnapshot(
    val cnpj: String,
    val assetType: String,
    val taxProfile: FundTaxProfile,
    val latestNav: FundDailyNav?,
)
