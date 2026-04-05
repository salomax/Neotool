package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.time.LocalDate

@Introspected
@Serdeable
data class ListedAssetDailyPriceConnectionDTO(
    val edges: List<ListedAssetDailyPriceEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,
)

@Introspected
@Serdeable
data class ListedAssetDailyPriceEdgeDTO(
    val node: ListedAssetDailyPriceNodeDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class ListedAssetDailyPriceNodeDTO(
    val ticker: String,
    val isinCode: String?,
    val date: LocalDate,
    val openPrice: Double?,
    val highPrice: Double?,
    val lowPrice: Double?,
    val closePrice: Double,
    val avgPrice: Double?,
    val volume: Double?,
    val trades: Int?,
    val updatedAt: Instant,
)

@Introspected
@Serdeable
data class FundDailyNavConnectionDTO(
    val edges: List<FundDailyNavEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,
)

@Introspected
@Serdeable
data class FundDailyNavEdgeDTO(
    val node: FundDailyNavNodeDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class FundDailyNavNodeDTO(
    val cnpj: String,
    val date: LocalDate,
    val closePrice: Double,
    val totalNetAssets: Double?,
    val updatedAt: Instant,
)

@Introspected
@Serdeable
data class EconomicIndicatorPointConnectionDTO(
    val edges: List<EconomicIndicatorPointEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,
)

@Introspected
@Serdeable
data class EconomicIndicatorPointEdgeDTO(
    val node: EconomicIndicatorPointNodeDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class EconomicIndicatorPointNodeDTO(
    val indicator: String,
    val variation: String,
    val unit: String?,
    val referenceDate: LocalDate,
    val publicationDate: LocalDate?,
    val value: Double,
    val updatedAt: Instant,
)

@Introspected
@Serdeable
data class AssetCorporateEventConnectionDTO(
    val edges: List<AssetCorporateEventEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,
)

@Introspected
@Serdeable
data class AssetCorporateEventEdgeDTO(
    val node: AssetCorporateEventNodeDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class AssetCorporateEventNodeDTO(
    val ticker: String?,
    val isinCode: String,
    val eventType: String,
    val factor: Double?,
    val priceUnit: Double?,
    val tradingStart: LocalDate?,
    val tradingEnd: LocalDate?,
    val subscriptionDate: LocalDate?,
    val lastDatePrior: LocalDate,
    val approvedOn: LocalDate,
    val remarks: String?,
    val updatedAt: Instant,
)
