package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.financialdata.domain.AssetCorporateEventSyncItem
import io.github.salomax.neotool.financialdata.domain.EconomicIndicatorPointSyncItem
import io.github.salomax.neotool.financialdata.domain.FundDailyNavSyncItem
import io.github.salomax.neotool.financialdata.domain.ListedAssetDailyPriceSyncItem
import io.github.salomax.neotool.financialdata.repo.AssetCorporateEventRepository
import io.github.salomax.neotool.financialdata.repo.IndicatorValueRepositoryCustom
import io.github.salomax.neotool.financialdata.repo.InvestmentFundRepositoryCustom
import io.github.salomax.neotool.financialdata.repo.ListedAssetRepositoryCustom
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate

@Singleton
class MarketDataSyncService(
    private val listedAssetRepository: ListedAssetRepositoryCustom,
    private val investmentFundRepository: InvestmentFundRepositoryCustom,
    private val indicatorValueRepository: IndicatorValueRepositoryCustom,
    private val assetCorporateEventRepository: AssetCorporateEventRepository,
) {
    companion object {
        private const val MAX_SYNC_PAGE_SIZE = 1_000
        private const val DEFAULT_SYNC_PAGE_SIZE = 250
    }

    fun listedAssetDailyPrices(
        from: LocalDate,
        to: LocalDate,
        tickers: Collection<String>?,
        updatedSince: Instant?,
        first: Int?,
        after: String?,
    ): Connection<ListedAssetDailyPriceSyncItem> {
        val pageSize = validateSyncPageSize(first)
        val cursor = after?.let(CursorEncoder::decodeCompositeCursorToString)
        val results =
            listedAssetRepository.getDailyPricesForSync(
                from = from,
                to = to,
                tickers = tickers,
                updatedSince = updatedSince,
                first = pageSize + 1,
                after = cursor,
            )
        val hasMore = results.size > pageSize
        return ConnectionBuilder.buildConnection(
            items = results.take(pageSize),
            hasMore = hasMore,
            encodeCursor = { item ->
                CursorEncoder.encodeCompositeCursor(
                    fieldValues = mapOf("date" to item.date.toString(), "ticker" to item.ticker),
                    id = item.assetId.toString(),
                )
            },
        )
    }

    fun fundDailyNavs(
        from: LocalDate,
        to: LocalDate,
        cnpjs: Collection<String>?,
        updatedSince: Instant?,
        first: Int?,
        after: String?,
    ): Connection<FundDailyNavSyncItem> {
        val pageSize = validateSyncPageSize(first)
        val cursor = after?.let(CursorEncoder::decodeCompositeCursorToString)
        val results =
            investmentFundRepository.getDailyNavsForSync(
                from = from,
                to = to,
                cnpjs = cnpjs,
                updatedSince = updatedSince,
                first = pageSize + 1,
                after = cursor,
            )
        val hasMore = results.size > pageSize
        return ConnectionBuilder.buildConnection(
            items = results.take(pageSize),
            hasMore = hasMore,
            encodeCursor = { item ->
                CursorEncoder.encodeCompositeCursor(
                    fieldValues = mapOf("date" to item.date.toString(), "cnpj" to item.cnpj),
                    id = item.assetId.toString(),
                )
            },
        )
    }

    fun economicIndicatorVariationPoints(
        from: LocalDate,
        to: LocalDate,
        indicators: Collection<String>?,
        variations: Collection<String>?,
        updatedSince: Instant?,
        first: Int?,
        after: String?,
    ): Connection<EconomicIndicatorPointSyncItem> {
        val pageSize = validateSyncPageSize(first)
        val cursor = after?.let(CursorEncoder::decodeCompositeCursorToString)
        val results =
            indicatorValueRepository.findPointsForSync(
                from = from,
                to = to,
                indicators = indicators,
                variations = variations,
                updatedSince = updatedSince,
                first = pageSize + 1,
                after = cursor,
            )
        val hasMore = results.size > pageSize
        return ConnectionBuilder.buildConnection(
            items = results.take(pageSize),
            hasMore = hasMore,
            encodeCursor = { item ->
                CursorEncoder.encodeCompositeCursor(
                    fieldValues =
                        mapOf(
                            "referenceDate" to item.referenceDate.toString(),
                            "indicator" to item.indicator,
                            "variation" to item.variation,
                        ),
                    id = item.variationId.toString(),
                )
            },
        )
    }

    fun assetCorporateEvents(
        from: LocalDate,
        to: LocalDate,
        tickers: Collection<String>?,
        isins: Collection<String>?,
        updatedSince: Instant?,
        first: Int?,
        after: String?,
    ): Connection<AssetCorporateEventSyncItem> {
        val pageSize = validateSyncPageSize(first)
        val cursor = after?.let(CursorEncoder::decodeCompositeCursorToString)
        val results =
            assetCorporateEventRepository.findStockEventsForSync(
                from = from,
                to = to,
                tickers = tickers,
                isins = isins,
                updatedSince = updatedSince,
                first = pageSize + 1,
                after = cursor,
            )
        val hasMore = results.size > pageSize
        return ConnectionBuilder.buildConnection(
            items = results.take(pageSize),
            hasMore = hasMore,
            encodeCursor = { item ->
                CursorEncoder.encodeCompositeCursor(
                    fieldValues =
                        mapOf(
                            "lastDatePrior" to item.lastDatePrior.toString(),
                            "isinCode" to item.isinCode,
                            "eventType" to item.eventType,
                            "approvedOn" to item.approvedOn.toString(),
                        ),
                    id = item.id.toString(),
                )
            },
        )
    }

    private fun validateSyncPageSize(first: Int?): Int {
        val pageSize = first ?: DEFAULT_SYNC_PAGE_SIZE
        require(pageSize in 1..MAX_SYNC_PAGE_SIZE) {
            "Parameter 'first' must be between 1 and $MAX_SYNC_PAGE_SIZE, got: $pageSize"
        }
        return pageSize
    }
}
