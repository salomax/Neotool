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
}
