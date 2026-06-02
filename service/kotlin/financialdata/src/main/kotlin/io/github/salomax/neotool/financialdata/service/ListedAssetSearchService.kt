package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.domain.AssetDailyPrice
import io.github.salomax.neotool.financialdata.domain.ListedAsset
import io.github.salomax.neotool.financialdata.repo.ListedAssetRepositoryCustom
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.LocalDate
import java.util.UUID

@TraceExecution
@Singleton
open class ListedAssetSearchService(
    private val assetRepository: ListedAssetRepositoryCustom,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Autocomplete search: ticker prefix first, then name trigram similarity.
     * Optionally filtered by [assetType].
     */
    fun searchAssetsAutocomplete(
        query: String,
        assetType: String?,
        limit: Int = 10,
    ): List<ListedAsset> {
        if (query.isBlank()) throw IllegalArgumentException("Query cannot be empty")
        return assetRepository.searchAssetsAutocomplete(query.trim(), assetType, limit)
    }

    /**
     * Paginated asset list/search with optional filters.
     * Supports Relay cursor pagination ordered by (ticker, id).
     */
    fun searchAssets(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int?,
        after: String?,
    ): Connection<ListedAsset> {
        val pageSize = PaginationConstants.validatePageSize(first)

        val afterCursor =
            after?.let {
                try {
                    CursorEncoder.decodeCompositeCursorToString(it)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid cursor: $after", e)
                }
            }

        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }
        val hasQuery = normalizedQuery != null

        val (results, totalCount) =
            assetRepository.searchAssets(
                query = normalizedQuery,
                assetType = assetType,
                isActive = isActive,
                first = pageSize + 1,
                after = afterCursor,
            )
        val hasMore = results.size > pageSize
        val page = results.take(pageSize)

        logger.debug {
            "Asset search: query=$normalizedQuery assetType=$assetType isActive=$isActive " +
                "returned=${page.size} total=$totalCount hasMore=$hasMore"
        }

        return ConnectionBuilder.buildConnection(
            items = page,
            hasMore = hasMore,
            encodeCursor = { asset ->
                val fieldValues =
                    if (hasQuery) {
                        mapOf("ticker" to asset.ticker, "id" to asset.id.toString(), "simScore" to asset.simScore)
                    } else {
                        mapOf("ticker" to asset.ticker, "id" to asset.id.toString())
                    }
                CursorEncoder.encodeCompositeCursor(
                    fieldValues = fieldValues,
                    id = asset.id.toString(),
                )
            },
            totalCount = totalCount,
        )
    }

    fun findAssetById(id: UUID): ListedAsset? = assetRepository.findAssetById(id)

    fun findAssetByTicker(ticker: String): ListedAsset? = assetRepository.findAssetByTicker(ticker.trim().uppercase())

    fun findAssetsByTickers(tickers: Set<String>): List<ListedAsset> =
        assetRepository.findAssetsByTickers(
            tickers.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet(),
        )

    fun getPriceHistory(
        assetId: UUID,
        from: LocalDate?,
        to: LocalDate?,
        last: Int?,
    ): List<AssetDailyPrice> = assetRepository.getPriceHistory(assetId, from, to, last)
}
