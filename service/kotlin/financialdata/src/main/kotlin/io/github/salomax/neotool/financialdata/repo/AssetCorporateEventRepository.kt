package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.AssetCorporateEventSyncItem
import java.time.Instant
import java.time.LocalDate

interface AssetCorporateEventRepository {
    fun findStockEventsForSync(
        from: LocalDate,
        to: LocalDate,
        tickers: Collection<String>?,
        isins: Collection<String>?,
        updatedSince: Instant?,
        first: Int,
        after: CompositeCursor?,
    ): List<AssetCorporateEventSyncItem>
}
