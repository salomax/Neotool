package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.LocalDate

enum class FundTaxProfile {
    COME_COTAS_LONG,
    COME_COTAS_SHORT,
    VARIABLE_INCOME_15,
    PENSION,
    UNKNOWN,
}

data class FundMarketNav(
    val date: LocalDate,
    val closePrice: BigDecimal,
)

data class FundMarketSnapshot(
    val fundCnpj: String,
    val assetType: String,
    val taxProfile: FundTaxProfile,
    val latestNav: FundMarketNav?,
)

interface FundMarketDataProvider {
    fun getSnapshotsByCnpj(cnpjs: Set<String>): Map<String, FundMarketSnapshot>
}
