package io.github.salomax.neotool.banking.client.investment.financialdata

import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import java.math.BigDecimal
import java.time.LocalDate

interface FundMarketDataProvider {
    fun getSnapshotsByCnpj(cnpjs: Set<String>): Map<String, FundMarketSnapshot>

    /** Daily NAV (date, close) in range [[from], [to]], ascending. Empty when unavailable. */
    fun getNavSeries(
        cnpj: String,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, BigDecimal>>
}
