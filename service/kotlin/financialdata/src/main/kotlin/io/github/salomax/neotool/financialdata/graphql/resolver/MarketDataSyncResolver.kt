package io.github.salomax.neotool.financialdata.graphql.resolver

import io.github.salomax.neotool.financialdata.graphql.dto.AssetCorporateEventConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.AssetCorporateEventEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.AssetCorporateEventNodeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorPointConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorPointEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorPointNodeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FundDailyNavConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FundDailyNavEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FundDailyNavNodeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetDailyPriceConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetDailyPriceEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetDailyPriceNodeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.financialdata.service.MarketDataSyncService
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate

@Singleton
class MarketDataSyncResolver(
    private val marketDataSyncService: MarketDataSyncService,
) {
    fun listedAssetDailyPrices(
        from: LocalDate,
        to: LocalDate,
        first: Int?,
        after: String?,
        updatedSince: Instant?,
        tickers: List<String>?,
    ): ListedAssetDailyPriceConnectionDTO {
        val connection = marketDataSyncService.listedAssetDailyPrices(from, to, tickers, updatedSince, first, after)
        return ListedAssetDailyPriceConnectionDTO(
            edges =
                connection.edges.map { edge ->
                    ListedAssetDailyPriceEdgeDTO(
                        node =
                            ListedAssetDailyPriceNodeDTO(
                                ticker = edge.node.ticker,
                                isinCode = edge.node.isinCode,
                                date = edge.node.date,
                                openPrice = edge.node.openPrice?.toDouble(),
                                highPrice = edge.node.highPrice?.toDouble(),
                                lowPrice = edge.node.lowPrice?.toDouble(),
                                closePrice = edge.node.closePrice.toDouble(),
                                avgPrice = edge.node.avgPrice?.toDouble(),
                                volume = edge.node.volume?.toDouble(),
                                trades = edge.node.trades,
                                updatedAt = edge.node.updatedAt,
                            ),
                        cursor = edge.cursor,
                    )
                },
            pageInfo = toPageInfoDTO(connection.pageInfo),
        )
    }

    fun fundDailyNavs(
        from: LocalDate,
        to: LocalDate,
        first: Int?,
        after: String?,
        updatedSince: Instant?,
        cnpjs: List<String>?,
    ): FundDailyNavConnectionDTO {
        val connection = marketDataSyncService.fundDailyNavs(from, to, cnpjs, updatedSince, first, after)
        return FundDailyNavConnectionDTO(
            edges =
                connection.edges.map { edge ->
                    FundDailyNavEdgeDTO(
                        node =
                            FundDailyNavNodeDTO(
                                cnpj = edge.node.cnpj,
                                date = edge.node.date,
                                closePrice = edge.node.closePrice.toDouble(),
                                totalNetAssets = edge.node.totalNetAssets?.toDouble(),
                                updatedAt = edge.node.updatedAt,
                            ),
                        cursor = edge.cursor,
                    )
                },
            pageInfo = toPageInfoDTO(connection.pageInfo),
        )
    }

    fun economicIndicatorVariationPoints(
        from: LocalDate,
        to: LocalDate,
        first: Int?,
        after: String?,
        updatedSince: Instant?,
        indicators: List<String>?,
        variations: List<String>?,
    ): EconomicIndicatorPointConnectionDTO {
        val connection =
            marketDataSyncService.economicIndicatorVariationPoints(
                from = from,
                to = to,
                indicators = indicators,
                variations = variations,
                updatedSince = updatedSince,
                first = first,
                after = after,
            )
        return EconomicIndicatorPointConnectionDTO(
            edges =
                connection.edges.map { edge ->
                    EconomicIndicatorPointEdgeDTO(
                        node =
                            EconomicIndicatorPointNodeDTO(
                                indicator = edge.node.indicator,
                                variation = edge.node.variation,
                                unit = edge.node.unit,
                                referenceDate = edge.node.referenceDate,
                                publicationDate = edge.node.publicationDate,
                                value = edge.node.value.toDouble(),
                                updatedAt = edge.node.updatedAt,
                            ),
                        cursor = edge.cursor,
                    )
                },
            pageInfo = toPageInfoDTO(connection.pageInfo),
        )
    }

    fun assetCorporateEvents(
        from: LocalDate,
        to: LocalDate,
        first: Int?,
        after: String?,
        updatedSince: Instant?,
        tickers: List<String>?,
        isins: List<String>?,
    ): AssetCorporateEventConnectionDTO {
        val connection = marketDataSyncService.assetCorporateEvents(from, to, tickers, isins, updatedSince, first, after)
        return AssetCorporateEventConnectionDTO(
            edges =
                connection.edges.map { edge ->
                    AssetCorporateEventEdgeDTO(
                        node =
                            AssetCorporateEventNodeDTO(
                                ticker = edge.node.ticker,
                                isinCode = edge.node.isinCode,
                                eventType = edge.node.eventType,
                                factor = edge.node.factor?.toDouble(),
                                priceUnit = edge.node.priceUnit?.toDouble(),
                                tradingStart = edge.node.tradingStart,
                                tradingEnd = edge.node.tradingEnd,
                                subscriptionDate = edge.node.subscriptionDate,
                                lastDatePrior = edge.node.lastDatePrior,
                                approvedOn = edge.node.approvedOn,
                                remarks = edge.node.remarks,
                                updatedAt = edge.node.updatedAt,
                            ),
                        cursor = edge.cursor,
                    )
                },
            pageInfo = toPageInfoDTO(connection.pageInfo),
        )
    }

    private fun toPageInfoDTO(pageInfo: io.github.salomax.neotool.common.graphql.pagination.PageInfo): PageInfoDTO =
        PageInfoDTO(
            hasNextPage = pageInfo.hasNextPage,
            hasPreviousPage = pageInfo.hasPreviousPage,
            startCursor = pageInfo.startCursor,
            endCursor = pageInfo.endCursor,
        )
}
