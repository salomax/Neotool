package io.github.salomax.neotool.financialdata.graphql.resolver

import io.github.salomax.neotool.financialdata.graphql.dto.AssetDailyPriceDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentAssetAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentAssetConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentAssetDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.InvestmentAssetMapper
import io.github.salomax.neotool.financialdata.service.InvestmentAssetSearchService
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL resolver for B3 investment asset queries.
 */
@Singleton
class InvestmentAssetResolver(
    private val assetSearchService: InvestmentAssetSearchService,
    private val mapper: InvestmentAssetMapper,
) {

    fun searchInvestmentAssetsAutocomplete(
        query: String,
        assetType: String?,
    ): InvestmentAssetAutocompleteConnectionDTO {
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
    ): InvestmentAssetConnectionDTO {
        val connection = assetSearchService.searchAssets(query, assetType, isActive, first, after)
        return mapper.toConnectionDTO(connection)
    }

    fun investmentAsset(id: String): InvestmentAssetDTO? {
        val uuid = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid asset id: $id")
        }
        return assetSearchService.findAssetById(uuid)?.let { mapper.toAssetDTO(it) }
    }

    fun investmentAssetByTicker(ticker: String): InvestmentAssetDTO? =
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
