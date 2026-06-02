package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ListedAssetDailyPriceSyncItem(
    val assetId: UUID,
    val ticker: String,
    val isinCode: String?,
    val date: LocalDate,
    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val closePrice: BigDecimal,
    val avgPrice: BigDecimal?,
    val volume: BigDecimal?,
    val trades: Int?,
    val updatedAt: Instant,
)

data class FundDailyNavSyncItem(
    val assetId: UUID,
    val cnpj: String,
    val date: LocalDate,
    val closePrice: BigDecimal,
    val totalNetAssets: BigDecimal?,
    val updatedAt: Instant,
)

data class EconomicIndicatorPointSyncItem(
    val variationId: UUID,
    val indicator: String,
    val variation: String,
    val unit: String?,
    val referenceDate: LocalDate,
    val publicationDate: LocalDate?,
    val value: BigDecimal,
    val updatedAt: Instant,
)

data class AssetCorporateEventSyncItem(
    val id: UUID,
    val assetId: UUID?,
    val ticker: String?,
    val isinCode: String,
    val eventType: String,
    val factor: BigDecimal?,
    val priceUnit: BigDecimal?,
    val tradingStart: LocalDate?,
    val tradingEnd: LocalDate?,
    val subscriptionDate: LocalDate?,
    val lastDatePrior: LocalDate,
    val approvedOn: LocalDate,
    val remarks: String?,
    val updatedAt: Instant,
)
