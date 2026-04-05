package io.github.salomax.neotool.banking.model.portfolio

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDate

@Embeddable
data class MarketIndicatorPointId(
    @Column(name = "indicator_label", nullable = false, length = 50)
    val indicatorLabel: String = "",
    @Column(name = "variation_label", nullable = false, length = 50)
    val variationLabel: String = "",
    @Column(name = "reference_date", nullable = false)
    val referenceDate: LocalDate = LocalDate.MIN,
) : Serializable
