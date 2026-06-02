package io.github.salomax.neotool.banking.model.portfolio

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "investment_asset_daily_price", schema = "banking")
open class InvestmentAssetDailyPriceEntity(
    @EmbeddedId
    open var id: InvestmentAssetDailyPriceId,
    @Column(name = "close_price", nullable = false, precision = 18, scale = 8)
    open var closePrice: BigDecimal,
    @Column(name = "currency_code", nullable = false, length = 3)
    open var currencyCode: String = "BRL",
    @Column(nullable = false, length = 50)
    open var source: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
