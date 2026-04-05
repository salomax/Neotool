package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketIndicatorPoint

interface MarketIndicatorPointRepository {
    fun upsert(point: MarketIndicatorPoint): MarketIndicatorPoint
}
