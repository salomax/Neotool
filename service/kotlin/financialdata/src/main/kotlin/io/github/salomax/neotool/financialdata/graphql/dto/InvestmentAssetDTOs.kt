package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDate

// Autocomplete DTOs

@Introspected
@Serdeable
data class ListedAssetAutocompleteConnectionDTO(
    val suggestions: List<ListedAssetAutocompleteItemDTO>,
)

@Introspected
@Serdeable
data class ListedAssetAutocompleteItemDTO(
    val id: String,
    val ticker: String,
    val name: String,
    val assetType: String,
    val isActive: Boolean,
)

// Paginated list DTOs

@Introspected
@Serdeable
data class ListedAssetConnectionDTO(
    val edges: List<ListedAssetEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int?,
)

@Introspected
@Serdeable
data class ListedAssetEdgeDTO(
    val node: ListedAssetDTO,
    val cursor: String,
)

// Asset detail DTO

@Introspected
@Serdeable
data class ListedAssetDTO(
    val id: String,
    val ticker: String,
    val name: String,
    val assetType: String,
    val exchange: String?,
    val isActive: Boolean,
    val latestPrice: AssetDailyPriceDTO?,
)

// OHLCV price DTO

@Introspected
@Serdeable
data class AssetDailyPriceDTO(
    val date: LocalDate,
    val openPrice: Double?,
    val highPrice: Double?,
    val lowPrice: Double?,
    val closePrice: Double,
    val avgPrice: Double?,
    val volume: Double?,
    val trades: Int?,
)
