package io.github.salomax.neotool.banking.model.portfolio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "investment_asset_corporate_event", schema = "banking")
open class InvestmentAssetCorporateEventEntity(
    @Id
    open var id: UUID,
    @Column(name = "asset_id")
    open var assetId: UUID? = null,
    @Column(length = 20)
    open var ticker: String? = null,
    @Column(name = "isin_code", nullable = false, length = 20)
    open var isinCode: String,
    @Column(name = "event_type", nullable = false, length = 30)
    open var eventType: String,
    @Column(precision = 22, scale = 8)
    open var factor: BigDecimal? = null,
    @Column(name = "price_unit", precision = 18, scale = 8)
    open var priceUnit: BigDecimal? = null,
    @Column(name = "trading_start")
    open var tradingStart: LocalDate? = null,
    @Column(name = "trading_end")
    open var tradingEnd: LocalDate? = null,
    @Column(name = "subscription_date")
    open var subscriptionDate: LocalDate? = null,
    @Column(name = "last_date_prior", nullable = false)
    open var lastDatePrior: LocalDate,
    @Column(name = "approved_on", nullable = false)
    open var approvedOn: LocalDate,
    @Column
    open var remarks: String? = null,
    @Column(nullable = false, length = 50)
    open var source: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
