package io.github.salomax.neotool.banking.model.portfolio

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDate
import java.util.UUID

@Embeddable
data class InvestmentAssetDailyPriceId(
    @Column(name = "asset_id", nullable = false)
    val assetId: UUID,
    @Column(name = "price_date", nullable = false)
    val priceDate: LocalDate,
) : Serializable
