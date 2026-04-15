package io.github.salomax.neotool.banking.client.investment.financialdata

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class FundNavBulkSyncNode(
    val cursor: String,
    val cnpj: String,
    val date: LocalDate,
    val closePrice: BigDecimal,
    val updatedAt: Instant,
)

interface FundNavBulkSyncClient {
    fun streamFundDailyNavs(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant? = null,
        cnpjs: Collection<String>? = null,
        after: String? = null,
        consumer: (FundNavBulkSyncNode) -> Unit,
    )
}
