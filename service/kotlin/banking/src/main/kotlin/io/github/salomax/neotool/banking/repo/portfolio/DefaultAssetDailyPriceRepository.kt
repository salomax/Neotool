package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.model.portfolio.InvestmentAssetDailyPriceMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultAssetDailyPriceRepository(
    private val entityRepository: AssetDailyPriceEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : AssetDailyPriceRepository {
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
}
