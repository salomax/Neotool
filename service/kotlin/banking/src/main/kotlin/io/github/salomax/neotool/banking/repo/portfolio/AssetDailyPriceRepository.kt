package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import java.time.LocalDate
import java.util.UUID

interface AssetDailyPriceRepository {
    fun upsert(price: InvestmentAssetDailyPrice): InvestmentAssetDailyPrice

    fun findByAssetAndDate(
        assetId: UUID,
        date: LocalDate,
    ): InvestmentAssetDailyPrice?

    fun findLatestBefore(
        assetId: UUID,
        date: LocalDate,
    ): InvestmentAssetDailyPrice?

    fun findByAssetAndDateRange(
        assetId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentAssetDailyPrice>

    /** For each asset in [assetIds], returns the most recent price on or before [date]. O(1) queries. */
    fun findLatestBeforeForAssets(
        assetIds: Set<UUID>,
        date: LocalDate,
    ): List<InvestmentAssetDailyPrice>

    /** Returns all prices for every asset in [assetIds] within [from]..[to]. O(1) queries. */
    fun findByAssetsAndDateRange(
        assetIds: Set<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentAssetDailyPrice>
}
