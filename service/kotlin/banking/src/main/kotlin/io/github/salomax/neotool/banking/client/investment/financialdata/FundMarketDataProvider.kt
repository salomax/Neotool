package io.github.salomax.neotool.banking.client.investment.financialdata

import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot

interface FundMarketDataProvider {
    fun getSnapshotsByCnpj(cnpjs: Set<String>): Map<String, FundMarketSnapshot>
}
