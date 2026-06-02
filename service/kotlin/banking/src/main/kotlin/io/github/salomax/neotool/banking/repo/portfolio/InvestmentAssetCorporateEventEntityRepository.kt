package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.model.portfolio.InvestmentAssetCorporateEventEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
interface InvestmentAssetCorporateEventEntityRepository : JpaRepository<InvestmentAssetCorporateEventEntity, UUID> {
    @Query(
        """
        SELECT e FROM InvestmentAssetCorporateEventEntity e
        WHERE e.isinCode = :isinCode
          AND e.eventType = :eventType
          AND e.lastDatePrior = :lastDatePrior
          AND e.approvedOn = :approvedOn
        """,
    )
    fun findByNaturalKey(
        isinCode: String,
        eventType: String,
        lastDatePrior: LocalDate,
        approvedOn: LocalDate,
    ): InvestmentAssetCorporateEventEntity?

    @Query(
        """
        INSERT INTO banking.investment_asset_corporate_event
            (asset_id, ticker, isin_code, event_type, factor, price_unit,
             trading_start, trading_end, subscription_date, last_date_prior,
             approved_on, remarks, source, created_at, updated_at)
        VALUES
            (:assetId, :ticker, :isinCode, :eventType, :factor, :priceUnit,
             :tradingStart, :tradingEnd, :subscriptionDate, :lastDatePrior,
             :approvedOn, :remarks, :source, NOW(), NOW())
        ON CONFLICT (isin_code, event_type, last_date_prior, approved_on)
        DO UPDATE SET
            asset_id           = EXCLUDED.asset_id,
            ticker             = EXCLUDED.ticker,
            factor             = EXCLUDED.factor,
            price_unit         = EXCLUDED.price_unit,
            trading_start      = EXCLUDED.trading_start,
            trading_end        = EXCLUDED.trading_end,
            subscription_date  = EXCLUDED.subscription_date,
            remarks            = EXCLUDED.remarks,
            source             = EXCLUDED.source,
            updated_at         = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        assetId: UUID?,
        ticker: String?,
        isinCode: String,
        eventType: String,
        factor: BigDecimal?,
        priceUnit: BigDecimal?,
        tradingStart: LocalDate?,
        tradingEnd: LocalDate?,
        subscriptionDate: LocalDate?,
        lastDatePrior: LocalDate,
        approvedOn: LocalDate,
        remarks: String?,
        source: String,
    )
}
