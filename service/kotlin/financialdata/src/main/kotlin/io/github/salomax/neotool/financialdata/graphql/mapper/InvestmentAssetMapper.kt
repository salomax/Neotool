package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.financialdata.domain.AssetDailyPrice
import io.github.salomax.neotool.financialdata.domain.ListedAsset
import io.github.salomax.neotool.financialdata.graphql.dto.AssetDailyPriceDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import jakarta.inject.Singleton

@Singleton
class ListedAssetMapper {

    fun toAutocompleteConnectionDTO(assets: List<ListedAsset>): ListedAssetAutocompleteConnectionDTO =
        ListedAssetAutocompleteConnectionDTO(
            suggestions = assets.map { toAutocompleteItemDTO(it) },
        )

    fun toAutocompleteItemDTO(asset: ListedAsset): ListedAssetAutocompleteItemDTO =
        ListedAssetAutocompleteItemDTO(
            id = asset.id.toString(),
            ticker = asset.ticker,
            name = asset.name,
            assetType = asset.assetType,
            isActive = asset.isActive,
        )

    fun toConnectionDTO(connection: Connection<ListedAsset>): ListedAssetConnectionDTO =
        ListedAssetConnectionDTO(
            edges = connection.edges.map { edge ->
                ListedAssetEdgeDTO(
                    node = toAssetDTO(edge.node),
                    cursor = edge.cursor,
                )
            },
            pageInfo = PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            ),
            totalCount = connection.totalCount?.toInt(),
        )

    fun toAssetDTO(asset: ListedAsset): ListedAssetDTO =
        ListedAssetDTO(
            id = asset.id.toString(),
            ticker = asset.ticker,
            name = asset.name,
            assetType = asset.assetType,
            exchange = asset.exchange,
            isActive = asset.isActive,
            latestPrice = asset.latestPrice?.let { toPriceDTO(it) },
        )

    fun toPriceDTO(price: AssetDailyPrice): AssetDailyPriceDTO =
        AssetDailyPriceDTO(
            date = price.date,
            openPrice = price.openPrice?.toDouble(),
            highPrice = price.highPrice?.toDouble(),
            lowPrice = price.lowPrice?.toDouble(),
            closePrice = price.closePrice.toDouble(),
            avgPrice = price.avgPrice?.toDouble(),
            volume = price.volume?.toDouble(),
            trades = price.trades,
        )

    fun toPriceListDTO(prices: List<AssetDailyPrice>): List<AssetDailyPriceDTO> =
        prices.map { toPriceDTO(it) }
}
