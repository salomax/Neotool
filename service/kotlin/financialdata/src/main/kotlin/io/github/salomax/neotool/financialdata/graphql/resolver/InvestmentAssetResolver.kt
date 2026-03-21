package io.github.salomax.neotool.financialdata.graphql.resolver

import io.github.salomax.neotool.financialdata.graphql.dto.AssetDailyPriceDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.ListedAssetMapper
import io.github.salomax.neotool.financialdata.service.ListedAssetSearchService
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL resolver for B3 investment asset queries.
 */
@Singleton
class InvestmentAssetResolver(
    private val assetSearchService: ListedAssetSearchService,
    private val mapper: ListedAssetMapper,
) {

    fun searchInvestmentAssetsAutocomplete(
        query: String,
        assetType: String?,
    ): ListedAssetAutocompleteConnectionDTO {
        if (query.isBlank()) throw IllegalArgumentException("Query cannot be empty")
        val assets = assetSearchService.searchAssetsAutocomplete(query, assetType)
        return mapper.toAutocompleteConnectionDTO(assets)
    }

    fun investmentAssets(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int?,
        after: String?,
    ): ListedAssetConnectionDTO {
        val connection = assetSearchService.searchAssets(query, assetType, isActive, first, after)
        return mapper.toConnectionDTO(connection)
    }

    fun investmentAsset(id: String): ListedAssetDTO? {
        val uuid = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid asset id: $id")
        }
        return assetSearchService.findAssetById(uuid)?.let { mapper.toAssetDTO(it) }
    }

    fun investmentAssetByTicker(ticker: String): ListedAssetDTO? =
        assetSearchService.findAssetByTicker(ticker)?.let { mapper.toAssetDTO(it) }

    fun priceHistory(
        assetId: String,
        from: LocalDate?,
        to: LocalDate?,
        last: Int?,
    ): List<AssetDailyPriceDTO> {
        val uuid = UUID.fromString(assetId)
        val history = assetSearchService.getPriceHistory(uuid, from, to, last)
        return mapper.toPriceListDTO(history)
    }
}
