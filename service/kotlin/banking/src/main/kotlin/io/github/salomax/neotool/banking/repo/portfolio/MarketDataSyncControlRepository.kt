package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncControl
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName

interface MarketDataSyncControlRepository {
    fun findByJobName(jobName: MarketDataSyncJobName): MarketDataSyncControl?

    fun findAll(): List<MarketDataSyncControl>

    fun upsert(control: MarketDataSyncControl): MarketDataSyncControl
}
