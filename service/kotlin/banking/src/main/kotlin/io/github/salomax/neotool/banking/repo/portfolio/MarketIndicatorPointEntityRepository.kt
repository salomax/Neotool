package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.model.portfolio.MarketIndicatorPointEntity
import io.github.salomax.neotool.banking.model.portfolio.MarketIndicatorPointId
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface MarketIndicatorPointEntityRepository : JpaRepository<MarketIndicatorPointEntity, MarketIndicatorPointId> {
    @Query(
        """
        SELECT p FROM MarketIndicatorPointEntity p
        WHERE p.id.indicatorLabel = :indicatorLabel
          AND p.id.variationLabel = :variationLabel
          AND p.id.referenceDate = :referenceDate
        """,
    )
    fun findByKey(
        indicatorLabel: String,
        variationLabel: String,
        referenceDate: LocalDate,
    ): MarketIndicatorPointEntity?

    @Query(
        """
        INSERT INTO banking.market_indicator_point
            (indicator_label, variation_label, reference_date, publication_date, value, source, created_at, updated_at)
        VALUES
            (:indicatorLabel, :variationLabel, :referenceDate, :publicationDate, :value, :source, NOW(), NOW())
        ON CONFLICT (indicator_label, variation_label, reference_date)
        DO UPDATE SET
            publication_date = EXCLUDED.publication_date,
            value            = EXCLUDED.value,
            source           = EXCLUDED.source,
            updated_at       = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        indicatorLabel: String,
        variationLabel: String,
        referenceDate: LocalDate,
        publicationDate: LocalDate?,
        value: BigDecimal,
        source: String,
    )
}
