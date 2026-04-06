package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.model.portfolio.InvestmentAssetDailyPriceEntity
import io.github.salomax.neotool.banking.model.portfolio.InvestmentAssetDailyPriceId
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
interface AssetDailyPriceEntityRepository :
    JpaRepository<InvestmentAssetDailyPriceEntity, InvestmentAssetDailyPriceId> {
    @Query(
        """
        SELECT p FROM InvestmentAssetDailyPriceEntity p
        WHERE p.id.assetId = :assetId
          AND p.id.priceDate = :date
        """,
    )
    fun findByKey(
        assetId: UUID,
        date: LocalDate,
    ): InvestmentAssetDailyPriceEntity?

    @Query(
        """
        SELECT p FROM InvestmentAssetDailyPriceEntity p
        WHERE p.id.assetId = :assetId
          AND p.id.priceDate <= :date
        ORDER BY p.id.priceDate DESC
        """,
    )
    fun findLatestBefore(
        assetId: UUID,
        date: LocalDate,
    ): List<InvestmentAssetDailyPriceEntity>

    @Query(
        """
        SELECT p FROM InvestmentAssetDailyPriceEntity p
        WHERE p.id.assetId = :assetId
          AND p.id.priceDate BETWEEN :from AND :to
        ORDER BY p.id.priceDate ASC
        """,
    )
    fun findByAssetAndDateRange(
        assetId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentAssetDailyPriceEntity>

    @Query(
        """
        SELECT DISTINCT ON (p.asset_id) p.asset_id, p.price_date, p.close_price, p.currency_code, p.source, p.created_at, p.updated_at
        FROM banking.investment_asset_daily_price p
        WHERE p.asset_id IN (:assetIds)
          AND p.price_date <= :date
        ORDER BY p.asset_id, p.price_date DESC
        """,
        nativeQuery = true,
    )
    fun findLatestBeforeForAssets(
        assetIds: Collection<UUID>,
        date: LocalDate,
    ): List<InvestmentAssetDailyPriceEntity>

    @Query(
        """
        SELECT p FROM InvestmentAssetDailyPriceEntity p
        WHERE p.id.assetId IN :assetIds
          AND p.id.priceDate BETWEEN :from AND :to
        ORDER BY p.id.priceDate ASC
        """,
    )
    fun findByAssetsAndDateRange(
        assetIds: Collection<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentAssetDailyPriceEntity>

    @Query(
        """
        INSERT INTO banking.investment_asset_daily_price
            (asset_id, price_date, close_price, currency_code, source, created_at, updated_at)
        VALUES
            (:assetId, :priceDate, :closePrice, :currencyCode, :source, NOW(), NOW())
        ON CONFLICT (asset_id, price_date)
        DO UPDATE SET
            close_price   = EXCLUDED.close_price,
            currency_code = EXCLUDED.currency_code,
            source        = EXCLUDED.source,
            updated_at    = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        assetId: UUID,
        priceDate: LocalDate,
        closePrice: BigDecimal,
        currencyCode: String,
        source: String,
    )
}
