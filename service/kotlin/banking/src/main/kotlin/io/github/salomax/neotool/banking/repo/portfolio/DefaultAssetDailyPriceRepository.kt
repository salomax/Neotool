package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.model.portfolio.InvestmentAssetDailyPriceMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

@Singleton
open class DefaultAssetDailyPriceRepository(
    private val entityRepository: AssetDailyPriceEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
    private val dataSource: DataSource,
) : AssetDailyPriceRepository {
    private val batchUpsertSql =
        """
        INSERT INTO banking.investment_asset_daily_price
            (asset_id, price_date, close_price, currency_code, source, created_at, updated_at)
        VALUES
            (?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (asset_id, price_date)
        DO UPDATE SET
            close_price   = EXCLUDED.close_price,
            currency_code = EXCLUDED.currency_code,
            source        = EXCLUDED.source,
            updated_at    = NOW()
        """.trimIndent()

    @Transactional
    override fun upsert(price: InvestmentAssetDailyPrice): InvestmentAssetDailyPrice {
        entityRepository.upsert(
            assetId = price.assetId,
            priceDate = price.priceDate,
            closePrice = price.closePrice,
            currencyCode = price.currencyCode,
            source = price.source,
        )
        entityManager.clear()
        return entityRepository.findByKey(price.assetId, price.priceDate)
            ?.let(InvestmentAssetDailyPriceMapper::toDomain)
            ?: throw IllegalStateException("Asset daily price upsert failed for ${price.assetId} @ ${price.priceDate}")
    }

    @Transactional
    override fun upsertAll(prices: List<InvestmentAssetDailyPrice>) {
        if (prices.isEmpty()) return
        dataSource.connection.use { connection ->
            connection.prepareStatement(batchUpsertSql).use { statement ->
                prices.forEach { price ->
                    statement.setObject(1, price.assetId)
                    statement.setObject(2, price.priceDate)
                    statement.setBigDecimal(3, price.closePrice)
                    statement.setString(4, price.currencyCode)
                    statement.setString(5, price.source)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
        entityManager.clear()
    }

    override fun findByAssetAndDate(
        assetId: UUID,
        date: LocalDate,
    ): InvestmentAssetDailyPrice? =
        entityRepository.findByKey(assetId, date)
            ?.let(InvestmentAssetDailyPriceMapper::toDomain)

    override fun findLatestBefore(
        assetId: UUID,
        date: LocalDate,
    ): InvestmentAssetDailyPrice? =
        entityRepository.findLatestBefore(assetId, date)
            .firstOrNull()
            ?.let(InvestmentAssetDailyPriceMapper::toDomain)

    override fun findByAssetAndDateRange(
        assetId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentAssetDailyPrice> =
        entityRepository.findByAssetAndDateRange(assetId, from, to)
            .map(InvestmentAssetDailyPriceMapper::toDomain)

    override fun findLatestBeforeForAssets(
        assetIds: Set<UUID>,
        date: LocalDate,
    ): List<InvestmentAssetDailyPrice> {
        if (assetIds.isEmpty()) return emptyList()
        return entityRepository.findLatestBeforeForAssets(assetIds, date)
            .map(InvestmentAssetDailyPriceMapper::toDomain)
    }

    override fun findByAssetsAndDateRange(
        assetIds: Set<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentAssetDailyPrice> {
        if (assetIds.isEmpty()) return emptyList()
        return entityRepository.findByAssetsAndDateRange(assetIds, from, to)
            .map(InvestmentAssetDailyPriceMapper::toDomain)
    }
}
