package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.AssetDailyPrice
import io.github.salomax.neotool.financialdata.domain.ListedAsset
import java.time.LocalDate
import java.util.UUID

/**
 * Custom query contract for B3 investment asset search and detail operations.
 */
interface ListedAssetRepositoryCustom {

    /**
     * Autocomplete search by ticker prefix first, then name trigram similarity.
     * Optionally filtered by [assetType] (e.g. "EQUITY", "FII").
     */
    fun searchAssetsAutocomplete(
        query: String,
        assetType: String?,
        limit: Int = 10,
    ): List<ListedAsset>

    /**
     * Paginated asset list/search with optional filters and Relay keyset pagination.
     * When [query] is null, returns all assets ordered by ticker.
     * When [query] is provided, orders by trigram similarity DESC then ticker ASC.
     * Returns (page, totalCount) — count computed via COUNT(*) OVER() to avoid extra round-trip.
     */
    fun searchAssets(
        query: String?,
        assetType: String?,
        isActive: Boolean?,
        first: Int,
        after: CompositeCursor?,
    ): Pair<List<ListedAsset>, Long>

    /**
     * Fetch a single asset by UUID. Returns null when not found.
     */
    fun findAssetById(id: UUID): ListedAsset?

    /**
     * Fetch a single asset by ticker (case-insensitive). Returns null when not found.
     */
    fun findAssetByTicker(ticker: String): ListedAsset?

    /**
     * Fetch multiple assets by ticker (case-insensitive) with latest price inline.
     */
    fun findAssetsByTickers(tickers: Set<String>): List<ListedAsset>

    /**
     * Fetch full OHLCV history for an asset.
     * [from]/[to] are inclusive bounds. [last] limits rows (most recent first).
     * Defaults to 365 rows when all are null.
     */
    fun getPriceHistory(
        assetId: UUID,
        from: LocalDate?,
        to: LocalDate?,
        last: Int?,
    ): List<AssetDailyPrice>
}
